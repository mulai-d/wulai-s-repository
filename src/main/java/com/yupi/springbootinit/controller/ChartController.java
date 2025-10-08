package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.FileConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.dto.file.UploadFileRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.FileUploadBizEnum;
import com.yupi.springbootinit.model.vo.BiResultVO;
import com.yupi.springbootinit.model.vo.MqMessageVO;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import com.yupi.springbootinit.znbimq.Producer;
import com.yupi.springbootinit.znbimq.ProducerRocketMq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Autowired
    private AiManager aiManager;

    @Autowired
    private RedisLimiterManager redisLimiterManager;


    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private Producer producer;

    @Autowired
    private ProducerRocketMq producerRocketMq;

    private final static Gson GSON = new Gson();

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResultVO> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                             GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        //参数校验
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name)&&name.length()>100, ErrorCode.PARAMS_ERROR,"名称过长");
        //文件名、文件大小校验
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> suffixs= Arrays.asList("xlsx", "xls", "csv", "xlsm", "xltx", "ods");
        ThrowUtils.throwIf(!suffixs.contains(suffix), ErrorCode.PARAMS_ERROR, "文件格式不允许");
        final long ONE_MB = 1024 * 1024;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小超过1mb");
        //限流
        User loginUser = userService.getLoginUser(request);
        redisLimiterManager.doLimiter("genChartByAi"+loginUser.getId());


        String csv = ExcelUtils.excelToCSV(multipartFile);
        //构造分析需求字符串
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师，接下来我会给你分析目标和数据，请给我分析结论");
        String userGoal=goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal=goal+",请使用"+chartType;
        }
        userInput.append("分析目标：").append(userGoal).append(chartType).append("/n").append("数据：").append(csv);


        //将结果数据存储到数据库
        Chart chart = new Chart();
        chart.setChartType(chartType);
        chart.setChartData(csv);
        chart.setName(name);
        chart.setGoal(goal);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        //提交图表生成业务任务
        CompletableFuture.runAsync(() -> {
            Chart taskChart = new Chart();
            Long id = chart.getId();
            taskChart.setId(id);
            taskChart.setStatus("running");
            boolean b = chartService.updateById(taskChart);
            if (!b){
                updateChartHandler(id, "修改图表状态为running失败");
                return;
            }


            //调用ai方法，并处理返回值
            String s = aiManager.sendMsgToXingHuo(true, userInput.toString());
            String[] split = s.split("'【【【【'");
            if (split.length < 3) {
                updateChartHandler(id, "ai生成图表失败");
                return;
            }
            for (int i = 0; i < split.length; i++) {
                System.out.println("结果 "+i+" ："+split[i]);
            }
            //trim(); 移除字符串首尾的所有空白字符，包括空格、制表符（tab）、换行符等。
            String genChart = split[1].trim();
            String genResult = split[2].trim();
            Chart resultChart = new Chart();
            resultChart.setId(id);
            resultChart.setGenChart(genChart);
            resultChart.setGenResult(genResult);
            resultChart.setStatus("succeed");
            boolean b1 = chartService.updateById(resultChart);
            if (!b1){
                updateChartHandler(id, "修改chart状态为succeed和保存图表信息失败");
                return;
            }
        }, threadPoolExecutor);


        BiResultVO biResultVO = new BiResultVO();
//        biResultVO.setGenChart(genChart);
//        biResultVO.setGenResult(genResult);
        biResultVO.setChartId(chart.getId());

        return ResultUtils.success(biResultVO);

    }


    private void updateChartHandler(Long chartId, String message) {
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setStatus("failed");
        chart.setExecMessage(message);
        boolean b = chartService.updateById(chart);
        if (!b) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改"+chartId+"图表状态为failed，添加执行信息失败");
        }
    }



    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResultVO> genChartByAiMq(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        //参数校验
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name)&&name.length()>100, ErrorCode.PARAMS_ERROR,"名称过长");
        //文件名、文件大小校验
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> suffixs= Arrays.asList("xlsx", "xls", "csv", "xlsm", "xltx", "ods");
        ThrowUtils.throwIf(!suffixs.contains(suffix), ErrorCode.PARAMS_ERROR, "文件格式不允许");
        final long ONE_MB = 1024 * 1024;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小超过1mb");
        //限流
        User loginUser = userService.getLoginUser(request);
        redisLimiterManager.doLimiter("genChartByAi"+loginUser.getId());


        String csv = ExcelUtils.excelToCSV(multipartFile);
        //构造分析需求字符串
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师，接下来我会给你分析目标和数据，请给我分析结论");
        String userGoal=goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal=goal+",请使用"+chartType;
        }
        userInput.append("分析目标：").append(userGoal).append(chartType).append("/n").append("数据：").append(csv);
        String userInputString = userInput.toString();


        //将结果数据存储到数据库
        Chart chart = new Chart();
        chart.setChartType(chartType);
        chart.setChartData(csv);
        chart.setName(name);
        chart.setGoal(goal);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        //使用rabbitmq提交任务，把任务提交至rabbitmq中
        MqMessageVO message = new MqMessageVO();
        message.setChart(chart);
        message.setUserInput(userInputString);

        log.info("准备向mq发送任务");
        producer.sendMessage(message);
        log.info("已经向mq发送任务");


        BiResultVO biResultVO = new BiResultVO();
//        biResultVO.setGenChart(genChart);
//        biResultVO.setGenResult(genResult);
        biResultVO.setChartId(chart.getId());

        return ResultUtils.success(biResultVO);

    }



    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/rocket/mq")
    public BaseResponse<BiResultVO> genChartByAiRocketMq(@RequestPart("file") MultipartFile multipartFile,
                                                   GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        //参数校验
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name)&&name.length()>100, ErrorCode.PARAMS_ERROR,"名称过长");
        //文件名、文件大小校验
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> suffixs= Arrays.asList("xlsx", "xls", "csv", "xlsm", "xltx", "ods");
        ThrowUtils.throwIf(!suffixs.contains(suffix), ErrorCode.PARAMS_ERROR, "文件格式不允许");
        final long ONE_MB = 1024 * 1024;
        long size = multipartFile.getSize();
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小超过1mb");
        //限流
        User loginUser = userService.getLoginUser(request);
        redisLimiterManager.doLimiter("genChartByAi"+loginUser.getId());


        String csv = ExcelUtils.excelToCSV(multipartFile);
//        //构造分析需求字符串
//        StringBuilder userInput = new StringBuilder();
////        userInput.append("你是一个数据分析师，接下来我会给你分析目标和数据，请给我分析结论");
        String userGoal=goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal=goal+",请使用"+chartType;
        }
//        userInput.append("分析目标：").append(userGoal).append(chartType).append("/n").append("数据：").append(csv);
//        String userInputString = userInput.toString();


        //将结果数据存储到数据库
        Chart chart = new Chart();
        chart.setChartType(chartType);
        chart.setChartData(csv);
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        //使用rabbitmq提交任务，把任务提交至rabbitmq中
        log.info("准备向mq发送任务");
        producerRocketMq.sendMessage(chart.getId().toString());
        log.info("已经向mq发送任务");


        BiResultVO biResultVO = new BiResultVO();
//        biResultVO.setGenChart(genChart);
//        biResultVO.setGenResult(genResult);
        biResultVO.setChartId(chart.getId());

        return ResultUtils.success(biResultVO);

    }


}

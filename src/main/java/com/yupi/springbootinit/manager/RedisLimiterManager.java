package com.yupi.springbootinit.manager;


import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.checkerframework.checker.units.qual.A;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisLimiterManager {

    @Autowired
    private RedissonClient redissonClient;


    public void doLimiter(String key){

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        //trySetRate方法具有幂等性
        rateLimiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);

        boolean tryAcquire = rateLimiter.tryAcquire(2);
        if(!tryAcquire){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "请求次数过多");
        }
    }


}

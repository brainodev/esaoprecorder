# ES AOP Recorder
ESAOPRecorder provides the capability to record all AOP JoinPoint into ElasticSearch.

## How to use

- Create your aspect class
- Configure the pointcut
- Start your application and use it

```java
import com.brainodev.gnfr.esaop.ESAOPRecorder;
import com.brainodev.gnfr.unittest.generator.UnitTestGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Aspect
@Slf4j
@Component
@Order
public class YourAspect {
    
    private static final String POINTCUT = "execution(* com.your.organization.package.*.*(..))";
    
    @Around(POINTCUT)
    public synchronized Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return ESAOPRecorder.around(joinPoint);
    }
    
    @AfterThrowing(pointcut = POINTCUT, throwing = "throwable")
    public synchronized void afterThrowing(JoinPoint joinPoint, Throwable throwable) {
        ESAOPRecorder.afterThrowing(joinPoint, throwable);
    }
}
```

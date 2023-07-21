package com.neu.webapp.restControllers;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.*;
import com.neu.webapp.errors.RegistrationStatus;
import com.neu.webapp.models.User;
import com.neu.webapp.services.UserService;
import com.neu.webapp.validators.UserValidator;
import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import org.springframework.http.HttpStatus;
import com.amazonaws.regions.Regions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import com.amazonaws.services.sns.AmazonSNS;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@RestController
public class UserRestController {
    private final static Logger LOGGER = LoggerFactory.getLogger(UserRestController.class);

    @Autowired
    private StatsDClient metricsClient;
    
    @Autowired
    private UserService userService;

    @Autowired
    private UserValidator userValidator;

    @InitBinder
    private void initBinder(WebDataBinder binder) {
        binder.setValidator(userValidator);
    }

    @GetMapping("/")
    public ResponseEntity<String> welcome(HttpServletRequest request) throws Exception{
        metricsClient.incrementCounter("endpoint./.http.get");
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String message = "Welcome  current time: "+sdf.format(cal.getTime());
        return ResponseEntity.status(HttpStatus.OK).body("{ \"message\": \""+message+"\" }");
    }

    @PostMapping("/user/register")
    public ResponseEntity<RegistrationStatus> register(@Valid @RequestBody User user, BindingResult errors, HttpServletResponse response) throws Exception{
        metricsClient.incrementCounter("endpoint./user./register.http.post");
        RegistrationStatus registrationStatus;
        if(errors.hasErrors()) {
            LOGGER.warn("User Registration Failed");
            registrationStatus = userService.getRegistrationStatus(errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(registrationStatus);
        }else {
            LOGGER.info("User Registration Successful");
            registrationStatus = new RegistrationStatus();
            userService.register(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(registrationStatus);
        }


    }

    @PostMapping("/reset")
    public ResponseEntity<String> passwordReset(@RequestBody User user) throws Exception{
        metricsClient.incrementCounter("endpoint./.http.reset");


        UserDetails u = userService.loadUserByUsername(user.getEmailId());

        if (u != null) {
            AmazonSNS snsClient = AmazonSNSClientBuilder.standard().withRegion(Regions.US_EAST_1).build();

            CreateTopicResult topicResult = snsClient.createTopic("email");
            String topicArn = topicResult.getTopicArn();

            final PublishRequest publishRequest = new PublishRequest(topicArn, user.getEmailId());
            LOGGER.warn("Reset request made"+publishRequest.getMessage());
            final PublishResult publishResponse = snsClient.publish(publishRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body("");
        } else {
            LOGGER.warn("Reset request Failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("");
        }

    }

    @GetMapping("/reset")
    public ResponseEntity<String> newPasswordReset(@RequestParam String email, @RequestParam String token) throws Exception{
        metricsClient.incrementCounter("endpoint./reset.http.get");
        if(email==null || token==null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\": \"token not found\"}");
        userService.update(email);
        return ResponseEntity.status(HttpStatus.OK).body("{\"newPassword\": \"P@$W0rD123\"}");
    }

}



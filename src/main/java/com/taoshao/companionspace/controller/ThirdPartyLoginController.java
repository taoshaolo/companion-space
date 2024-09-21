package com.taoshao.companionspace.controller;

import com.taoshao.companionspace.common.BaseResponse;
import com.taoshao.companionspace.common.ErrorCode;
import com.taoshao.companionspace.common.ResultUtil;
import com.taoshao.companionspace.contant.UserConstant;
import com.taoshao.companionspace.exception.BusinessException;
import com.taoshao.companionspace.model.entity.User;
import com.taoshao.companionspace.model.request.QQLoginRequest;
import com.taoshao.companionspace.service.ThirdPartyLoginService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @Author: taoshao
 * @Date: 2023年04月24日 09:39
 * @Version: 1.0
 * @Description:
 */
@RestController
@RequestMapping("/login")
public class ThirdPartyLoginController {
    @Resource
    private ThirdPartyLoginService thirdPartyLoginService;

    @GetMapping("/qq")
    public BaseResponse<String> qqLogin() throws IOException {
        String url = thirdPartyLoginService.qqLogin();
        return ResultUtil.success(url);
    }

    @PostMapping("/loginInfo")
    public BaseResponse<User> saveLoginInfo(@RequestBody QQLoginRequest qqLoginRequest, HttpServletRequest request) throws IOException {
        if (qqLoginRequest == null || StringUtils.isBlank(qqLoginRequest.getCode())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请重新登录");
        }
        User user = thirdPartyLoginService.getLoginInfo(qqLoginRequest, request);
        request.getSession().setAttribute(UserConstant.LOGIN_USER_STATUS, user);
        return ResultUtil.success(user);
    }
}

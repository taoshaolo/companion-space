package com.taoshao.companionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.taoshao.companionspace.common.ErrorCode;
import com.taoshao.companionspace.config.ThirdPartyLoginConfig;
import com.taoshao.companionspace.exception.BusinessException;
import com.taoshao.companionspace.model.entity.User;
import com.taoshao.companionspace.model.request.QQLoginRequest;
import com.taoshao.companionspace.model.vo.LoginInfoVo;
import com.taoshao.companionspace.model.vo.QQLoginVo;
import com.taoshao.companionspace.service.ThirdPartyLoginService;
import com.taoshao.companionspace.service.UserService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @Author: taoshao
 * @Date: 2023年04月24日 16:03
 * @Version: 1.0
 * @Description:
 */
@Service
public class ThirdPartyLoginImpl implements ThirdPartyLoginService {
    @Resource
    private ThirdPartyLoginConfig thirdPartyLoginConfig;

    @Resource
    private UserService userService;

    @Override
    public User getLoginInfo(QQLoginRequest qqLoginRequest, HttpServletRequest request) throws IOException {
        String requestUrl = String.format("https://uniqueker.top/connect.php?act=callback&appid=%s&appkey=%s&type=qq&code=%s"
                , thirdPartyLoginConfig.getAppId(), thirdPartyLoginConfig.getAppKey(), qqLoginRequest.getCode());
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();
            HttpGet requestBody = new HttpGet(requestUrl);
            response = client.execute(requestBody);
            String responseBody = EntityUtils.toString(response.getEntity());
            LoginInfoVo infoVo = new Gson().fromJson(responseBody, LoginInfoVo.class);
            User user = new User();
            if (infoVo.getCode().equals(0)) {
                user.setUserAccount(infoVo.getSocial_uid());
                user.setUsername(infoVo.getNickname());
                user.setUserAvatarUrl(infoVo.getFaceimg());
                user.setTags("[]");
                user.setTeamIds("[]");
                user.setUserIds("[]");
                LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
                userLambdaQueryWrapper.eq(User::getUserAccount, user.getUserAccount());
                User one = userService.getOne(userLambdaQueryWrapper);
                if (one != null) {
                    return one;
                } else {
                    if ("男".equals(infoVo.getGender())) {
                        user.setGender(1);
                    } else {
                        user.setGender(2);
                    }
                    userService.save(user);
                }
                return user;
            } else {
                throw new BusinessException(ErrorCode.NOT_LOGIN, "请重新登录");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            assert response != null;
            response.close();
            client.close();
        }
    }

    @Override
    public String qqLogin() throws IOException {
        String requestUrl = String.format("https://uniqueker.top/connect.php?act=login&appid=%s&appkey=%s&type=qq&redirect_uri=%s",
                thirdPartyLoginConfig.getAppId(), thirdPartyLoginConfig.getAppKey(), thirdPartyLoginConfig.getRedirectUrl());
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();
            HttpGet request = new HttpGet(requestUrl);
            response = client.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());
            QQLoginVo qq = new Gson().fromJson(responseBody, QQLoginVo.class);
            return qq.getUrl();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            assert response != null;
            response.close();
        }
    }
}

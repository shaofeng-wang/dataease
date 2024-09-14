package io.dataease.commons.utils;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.lib.fn.ELFunctionDefinition;
import io.dataease.auth.api.dto.CurrentUserDto;
import io.dataease.auth.entity.SysUserEntity;
import io.dataease.auth.service.AuthUserService;
import io.dataease.auth.util.JWTUtils;
import io.dataease.dto.SqlVarParamDTO;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JinJavaUtils {

    @Autowired
    private AuthUserService authUserService;

    public static String today(String format) {
        return DateUtil.format(DateUtil.date(), format);
    }

    public static String yesterday(String format) {
        return DateUtil.format(DateUtil.yesterday(), format);
    }

    public static String tomorrow(String format) {
        return DateUtil.format(DateUtil.tomorrow(), format);
    }

    public static Integer stats_month_days(String ym) {
        DateTime dateYm = DateUtil.parse(ym, "yyyy-MM");
        Date today = DateUtil.date();
        boolean isSame = DateUtil.isSameMonth(today, dateYm);
        if (isSame) {
            return NumberUtil.parseInt(today("dd")) - 1;
        }
        if (DateUtil.compare(today, dateYm, "yyyy-MM") < 0) {
            return 0;
        }
        return DateUtil.lengthOfMonth(dateYm.month(), DateUtil.isLeapYear(dateYm.year()));
    }

    public static List<Integer> stats_month_days_list(String ym) {
        Integer days = stats_month_days(ym);
        List<Integer> days_list = Lists.newArrayList();
        for (int i = 1; i <= days; ++i) {
            days_list.add(i);
        }
        return days_list;
    }

    public static void main(String[] args) {
        System.out.println(stats_month_days("2024-10"));
        System.out.println(stats_month_days("2024-09"));
        System.out.println(stats_month_days("2024-08"));
        System.out.println(stats_month_days_list("2024-09"));
        System.out.println(today("dd"));
        System.out.println(yesterday("dd"));
        System.out.println(tomorrow("dd"));
    }

    public String handleJinJavaTemplate(String sql, List<SqlVarParamDTO> sqlVarList) {
        Jinjava jinjava = new Jinjava();
        // 注册日期函数
        jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition("dt", "today", JinJavaUtils.class, "today", String.class));
        jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition("dt", "yesterday", JinJavaUtils.class, "yesterday", String.class));
        jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition("dt", "tomorrow", JinJavaUtils.class, "tomorrow", String.class));
        jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition("dt", "stats_month_days", JinJavaUtils.class, "stats_month_days", String.class));
        jinjava.getGlobalContext().registerFunction(new ELFunctionDefinition("dt", "stats_month_days_list", JinJavaUtils.class, "stats_month_days", String.class));
        Map<String, Object> context = Maps.newHashMap();
        for (SqlVarParamDTO var : sqlVarList) {
            context.put(var.getParamName(), var.getFilter());
        }
        CurrentUserDto userDto = (CurrentUserDto) SecurityUtils.getSubject().getPrincipal();
        if (ObjectUtils.isEmpty(userDto)) {
            String token = ServletUtils.getToken();
            if (ObjectUtils.isNotEmpty(token)) {
                Long userId = JWTUtils.tokenInfoByToken(token).getUserId();
                SysUserEntity user = authUserService.getUserById(userId);
                userDto = BeanUtils.copyBean(new CurrentUserDto(), user, "password");
            } else {
                userDto = new CurrentUserDto();
            }
        } else {
            userDto = BeanUtils.copyBean(new CurrentUserDto(), userDto, "password");
        }
        context.put("user", userDto);
        return jinjava.render(sql, context);
    }

    public String encodeToString(String encoded) {
        return Base64.getEncoder().encodeToString(encoded.getBytes());
    }
}

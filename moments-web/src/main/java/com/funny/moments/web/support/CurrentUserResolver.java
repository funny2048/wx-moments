package com.funny.moments.web.support;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.consts.PatternConsts;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.utils.CookieUtils;

/**
 * 当前视角用户（viewer）解析器（design §6.1，A3/B3 定稿）：
 * query 参数 viewerId 显式覆盖优先，缺省回退 Cookie mockUserId（前端用户切换器写入，C3）。
 *
 * <p>两套值的校验口径刻意不同（B3）：
 * <ul>
 * <li>viewerId 参数为「显式传入」——非法（非 1-18 位纯数字 / 0 / 超 Long 范围）一律抛 1001，
 *     不静默回退 Cookie（显式参数错误必须显式暴露）</li>
 * <li>Cookie 值为「环境脏数据」——非法宽松处理，视为未选择（requireUser 转 1003），不抛错</li>
 * </ul>
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public final class CurrentUserResolver {

    /** viewer 显式覆盖参数名（非 userId，避免与 GET /api/posts?userId= 的目标作者语义冲突，C14） */
    private static final String VIEWER_PARAM = "viewerId";

    /** mock 用户 Cookie 名（前端用户切换器写入） */
    private static final String MOCK_USER_COOKIE = "mockUserId";

    private CurrentUserResolver() {
    }

    /**
     * 解析当前视角用户，可能返回 null（viewerId 参数与 Cookie 均无有效值=未选择）。
     */
    public static Long resolve(HttpServletRequest request) {
        // 1 显式 viewerId 参数优先（A3）：存在即强校验，非法抛 1001，不静默回退 Cookie（B3）
        String viewerId = request.getParameter(VIEWER_PARAM);
        if (StringUtils.isNotBlank(viewerId)) {
            // 正则限 1-18 位数字：格式非法与超 18 位（Long 溢出）在此拦截
            if (!PatternConsts.USER_ID.matcher(viewerId).matches()) {
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
            try {
                Long viewer = Long.valueOf(viewerId);
                if (viewer <= 0) {
                    // 防 user_id=0 脏数据穿透（B3，上下界同时校验）
                    throw bizException(SocialErrorCode.PARAM_INVALID);
                }
                return viewer;
            } catch (NumberFormatException e) {
                // 双保险：18 位上限下理论不可达，防口径变更后溢出落框架兜底 code=100
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
        }
        // 2 Cookie mockUserId：脏值宽松——非 1-18 位纯数字或 0 视为未选择返回 null（由 requireUser 转 1003）
        String cookieValue = CookieUtils.getCookieValueByName(request, MOCK_USER_COOKIE);
        if (StringUtils.isNotBlank(cookieValue) && PatternConsts.USER_ID.matcher(cookieValue).matches()) {
            try {
                Long viewer = Long.valueOf(cookieValue);
                return viewer > 0 ? viewer : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 解析当前视角用户，未选择（无有效 viewerId 参数与 Cookie）抛 1003。
     */
    public static Long requireUser(HttpServletRequest request) {
        Long viewer = resolve(request);
        if (Objects.isNull(viewer)) {
            throw bizException(SocialErrorCode.CURRENT_USER_REQUIRED);
        }
        return viewer;
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值。
     */
    private static BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}

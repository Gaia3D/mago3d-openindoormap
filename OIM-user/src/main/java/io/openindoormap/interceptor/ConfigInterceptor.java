package io.openindoormap.interceptor;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import io.openindoormap.domain.Key;
import io.openindoormap.domain.YOrN;
import io.openindoormap.domain.cache.CacheManager;
import io.openindoormap.domain.menu.Menu;
import io.openindoormap.domain.user.UserGroupMenu;
import io.openindoormap.domain.user.UserSession;
import lombok.extern.slf4j.Slf4j;

/**
 * 사이트 전체 설정 관련 처리를 담당
 *
 * @author jeongdae
 */
@Slf4j
@Component
public class ConfigInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();
        HttpSession session = request.getSession();

        // TODO 너무 비 효율 적이다. 좋은 방법을 찾자.
        // 세션이 존재하지 않는 경우
        UserSession userSession = (UserSession) session.getAttribute(Key.USER_SESSION.name());
//    	if(userSession != null && userSession.getUserId() != null && !"".equals(userSession.getUserId())) {
        int userGroupId = userSession == null ? 1 : userSession.getUserGroupId();
        List<UserGroupMenu> userGroupMenuList = CacheManager.getUserGroupMenuList(userGroupId);
        Integer clickParentId = null;
        Integer clickMenuId = null;
        Integer clickDepth = null;
        Menu menu = null;
        Menu parentMenu = null;

        for (UserGroupMenu userGroupMenu : userGroupMenuList) {
            if (uri.equals(userGroupMenu.getUrl())) {
                clickMenuId = userGroupMenu.getMenuId();
                if (userGroupMenu.getDepth() == 1) {
                    clickParentId = userGroupMenu.getMenuId();
                } else {
                    clickParentId = Integer.valueOf(userGroupMenu.getParent().toString());
                }
                clickDepth = userGroupMenu.getDepth();

                if (userGroupMenu.getDepth() == 1 && (uri.indexOf("/main/index") >= 0)) {
                    break;
                } else if (userGroupMenu.getDepth() == 2) {
                    break;
                } else {
                    // pass
                }
            }
        }

        menu = CacheManager.getMenuMap().get(clickMenuId);
        parentMenu = CacheManager.getMenuMap().get(clickParentId);
        if (menu != null) {
            if (YOrN.Y == YOrN.valueOf(menu.getDisplayYn())) {
                menu.setAliasName(null);
                parentMenu.setAliasName(null);
            } else {
                Integer aliasMenuId = CacheManager.getMenuUrlMap().get(menu.getUrlAlias());
                Menu aliasMenu = CacheManager.getMenuMap().get(aliasMenuId);
                menu.setAliasName(aliasMenu.getName());
                parentMenu.setAliasName(aliasMenu.getName());
            }
        }

        request.setAttribute("clickMenuId", clickMenuId);
        request.setAttribute("menu", menu);
        request.setAttribute("parentMenu", parentMenu);
        request.setAttribute("cacheUserGroupMenuList", userGroupMenuList);
        request.setAttribute("cacheUserGroupMenuListSize", userGroupMenuList.size());
        request.setAttribute("contentCacheVersion", CacheManager.getPolicy().getContentCacheVersion());
//    	}

        return true;
    }
}

package emotes;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import common.ApiResponse;
import com.google.gson.Gson;

/** Registered after AuthFilter and before legacy guest/CSRF filters. */
public final class EmoteFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
        HttpServletRequest req=(HttpServletRequest)request;HttpServletResponse res=(HttpServletResponse)response;
        res.setHeader("Cache-Control","no-store");res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
        HttpSession session=req.getSession(false);
        String username=session==null?null:(String)session.getAttribute("username");
        String role=(String)req.getAttribute("role");String path=req.getPathInfo();
        boolean guest="guest".equals(role)||"Guest User".equals(username);
        if(guest && path!=null && (path.equals("/mine")||path.startsWith("/mine/"))) {fail(res,403,"GUEST_RESTRICTED");return;}
        if(!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())) {
            Object token=session==null?null:session.getAttribute("csrfToken");
            if(!(token instanceof String) || ((String)token).isEmpty() || !token.equals(req.getHeader("X-XSRF-TOKEN"))) {fail(res,403,"CSRF_INVALID");return;}
        }
        try {
            // Reads repeat access inside their data transaction. Unsupported routes also get strict access.
            if(!req.getMethod().equals("GET") || !Set.of("/categories","/catalog").contains(path==null?"":path)) new EmoteService().access(username,role);
            chain.doFilter(req,res);
        }catch(EmoteService.Failure e){fail(res,e.status,e.code);}
    }
    static void fail(HttpServletResponse res,int status,String code) throws IOException {
        res.setStatus(status);res.getWriter().print(new Gson().toJson(ApiResponse.fail("Emote request could not be completed.",code)));
    }
}

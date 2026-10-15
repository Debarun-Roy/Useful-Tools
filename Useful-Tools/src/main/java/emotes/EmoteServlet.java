package emotes;

import java.io.IOException;
import jakarta.servlet.http.*;
import common.ApiResponse;
import com.google.gson.Gson;

public final class EmoteServlet extends HttpServlet {
    @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws IOException {
        if(!"GET".equals(req.getMethod())) {res.setHeader("Allow","GET");EmoteFilter.fail(res,405,"METHOD_NOT_ALLOWED");return;}
        String path=req.getPathInfo();
        if(!"/categories".equals(path) && !"/catalog".equals(path)){EmoteFilter.fail(res,404,"NOT_FOUND");return;}
        try {
            Object data=new EmoteService().read((String)req.getAttribute("username"),(String)req.getAttribute("role"),req.getQueryString(),"/categories".equals(path));
            res.getWriter().print(new Gson().toJson(ApiResponse.ok(data)));
        }catch(EmoteService.Failure e){EmoteFilter.fail(res,e.status,e.code);}
    }
}

package com.filemanager.api;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.*;
@WebServlet("/api/command") public class CommandServlet extends HttpServlet{
 protected void doPost(HttpServletRequest r,HttpServletResponse p)throws IOException{
  if(r.getSession(false)==null||r.getSession(false).getAttribute("USERNAME")==null){ApiUtil.error(p,401,"Not authenticated");return;}
  String b=new String(r.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);String path=val(b,"path"),cmd=val(b,"command");if(!FileService.NATIVE_COMMANDS){ApiUtil.error(p,403,"Native commands disabled");return;}try{String out=FileService.process(cmd,path);ApiUtil.json(p,200,"{\"output\":\""+ApiUtil.esc(out)+"\"}");}catch(Exception e){ApiUtil.error(p,500,e.getMessage());}
 }
 private String val(String b,String k){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\""+k+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\"])*)\\\"").matcher(b);return m.find()?m.group(1).replace("\\\"","\"").replace("\\\\","\\"):"";}
}

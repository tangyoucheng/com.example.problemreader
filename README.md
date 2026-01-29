１、Eclipse版本:
Eclipse IDE for RCP and RAP Developers (includes Incubating components)  
  
Version: 2025-12 (4.38.0)  
Build id: 20251204-0850  
  


２、eclipse的插件项目点击右键  
Run As  
>⇒Eclipse Application  
  
３、之后再修改运行参数  
eclipse application的main  
>⇒Program to Run  
>>⇒com.example.problemreader.Application (选择)  
>>和plugin.xml里面这个类是同一个
>>       <run class="com.example.problemreader.Application"></run>  
    
eclipse application的Arguments  
>⇒Program arguments  
>>⇒-data C:\workspace_check (追加参数)  
  
4、启动后，如果有错误常见的就是Plug-ins 页面里面选的不匹配  
eclipse application的Plug-ins  
>⇒Target Platform  


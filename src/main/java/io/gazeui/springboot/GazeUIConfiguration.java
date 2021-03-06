/*
 * MIT License
 * 
 * Copyright (c) 2019 Rosberg Linhares (rosberglinhares@gmail.com)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.gazeui.springboot;

import java.lang.reflect.Method;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.gazeui.springboot.annotation.EnableGazeUI;
import io.gazeui.springboot.http.MediaTypeExtensions;
import io.gazeui.ui.Window;

@Configuration
@ComponentScan("io.gazeui.springboot")
public class GazeUIConfiguration {
    
    public static final String CREATE_INITIAL_UI_URL_PATH = "create-initial-ui";
    public static final String PROCESS_SERVER_UI_EVENT_URL_PATH = "process-server-ui-event";
    
    private final EnableGazeUI enableGazeUIAnnotation;
    private String htmlBaseUrl;
    
    @Autowired
    public GazeUIConfiguration(ApplicationContext applicationContext) {
        // Get the first EnableGazeUI annotation and ignore the other ones
        String beanNameWithEnableGazeUI = applicationContext.getBeanNamesForAnnotation(EnableGazeUI.class)[0];
        this.enableGazeUIAnnotation = applicationContext.findAnnotationOnBean(beanNameWithEnableGazeUI, EnableGazeUI.class);
        
        this.setHtmlBaseUrl(this.enableGazeUIAnnotation.basePath());
    }
    
    private void setHtmlBaseUrl(String gazeUIBasePath) {
        // Set a <base> element is necessary because 'child-path' relative to 'http://localhost/parent-path/' is
        // 'http://localhost/parent-path/child-path' but 'child-path' relative to 'http://localhost/parent-path' is
        // 'http://localhost/child-path'.
        // So when the GazeUI base path is '/level1/level2', for example, we have to set the HTML base element
        // to 'level2/'.
        if (gazeUIBasePath.isEmpty() || gazeUIBasePath.endsWith("/")) {
            this.htmlBaseUrl = null;
        } else {
            int posLastSlash = gazeUIBasePath.lastIndexOf("/");
            
            if (posLastSlash != -1) {
                this.htmlBaseUrl = gazeUIBasePath.substring(posLastSlash + 1);
            } else {
                this.htmlBaseUrl = gazeUIBasePath;
            }
            
            this.htmlBaseUrl += "/";
        }
    }
    
    public Class<? extends Window> getMainWindowClass() {
        return this.enableGazeUIAnnotation.mainWindowClass();
    }
    
    public String getHtmlBaseUrl() {
        return this.htmlBaseUrl;
    }

    @Autowired
    public void setDynamicHandlerMappings(RequestMappingHandlerMapping mapping, GazeUIController gazeUIController) {
        Method getInitialHtmlMethod;
        Method getInitialUICreationScriptMethod;
        Method processServerUIEventMethod;
        
        try {
            getInitialHtmlMethod = GazeUIController.class.getDeclaredMethod("getInitialHtml");
            getInitialUICreationScriptMethod = GazeUIController.class.getDeclaredMethod(
                    "getInitialUICreationScript", HttpSession.class);
            processServerUIEventMethod = GazeUIController.class.getDeclaredMethod(
                    "processServerUIEvent", ServerUIEventInfo.class, HttpSession.class);
        } catch (NoSuchMethodException | SecurityException ex) {
            // Never happens, once the methods will always be declared
            throw new RuntimeException(ex);
        }
        
        RequestMappingInfo getInitialHtmlMappingInfo = RequestMappingInfo
                .paths(this.enableGazeUIAnnotation.basePath())
                .methods(RequestMethod.GET)
                .produces(MediaType.TEXT_HTML_VALUE)
                .build();
        
        RequestMappingInfo getInitialUICreationScriptMappingInfo = RequestMappingInfo
                .paths(this.enableGazeUIAnnotation.basePath() + "/" + CREATE_INITIAL_UI_URL_PATH)
                .methods(RequestMethod.GET)
                .produces(MediaTypeExtensions.APPLICATION_JAVASCRIPT_VALUE)
                .build();
        
        RequestMappingInfo processServerUIEventMappingInfo = RequestMappingInfo
                .paths(this.enableGazeUIAnnotation.basePath() + "/" + PROCESS_SERVER_UI_EVENT_URL_PATH)
                .methods(RequestMethod.POST)
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaTypeExtensions.APPLICATION_JAVASCRIPT_VALUE)
                .build();
        
        mapping.registerMapping(getInitialHtmlMappingInfo, gazeUIController, getInitialHtmlMethod);
        mapping.registerMapping(getInitialUICreationScriptMappingInfo, gazeUIController, getInitialUICreationScriptMethod);
        mapping.registerMapping(processServerUIEventMappingInfo, gazeUIController, processServerUIEventMethod);
    }
}
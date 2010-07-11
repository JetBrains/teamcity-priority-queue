/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.serverSide.priority.controllers;

import jetbrains.buildServer.controllers.*;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * @author dmitry.neverov
 */
public class EditPriorityClassConfigurationsController extends BaseController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;
  private final String myDefaultPriorityClassListUrl;

  public EditPriorityClassConfigurationsController(@NotNull final PluginDescriptor pluginDescriptor,
                                                   @NotNull final WebControllerManager manager,
                                                   @NotNull final PriorityClassManager pClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = pClassManager;
    myDefaultPriorityClassListUrl = myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html";
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "priorityClassConfigurations.html", this);
  }

  @Override
  protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    PriorityClass priorityClass = getPriorityClass(request);
    if (priorityClass == null) {
      getOrCreateMessages(request).addMessage("priorityClassNotFound", "Selected priority class does not exist anymore");
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    if (priorityClass.isDefault()) {
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("priorityClassConfigurations.jsp"));
    mv.getModel().put("priorityClass", priorityClass);
    List<SBuildType> sortedBuildTypes = priorityClass.getBuildTypes();
    Collections.sort(sortedBuildTypes);
    mv.getModel().put("sortedBuildTypes", sortedBuildTypes);
    return mv;
  }

  private PriorityClass getPriorityClass(final HttpServletRequest request) {
    String priorityClassId = request.getParameter("priorityClassId");
    if (priorityClassId != null) {
      return myPriorityClassManager.findPriorityClassById(priorityClassId);
    } else {
      return null;
    }
  }
}

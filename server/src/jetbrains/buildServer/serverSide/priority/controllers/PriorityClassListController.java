/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author dmitry.neverov
 */
public class PriorityClassListController extends BaseController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;

  public PriorityClassListController(@NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final PriorityClassManager pClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = pClassManager;
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html", this);
  }

  @Override
  protected ModelAndView doHandle(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    ModelAndView view = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("priorityClassList.jsp"));
    final Map model = view.getModel();
    model.put("priorityClasses", getPriorityClasses());
    return view;
  }

  private Collection<PriorityClass> getPriorityClasses() {
    List<PriorityClass> pClasses = myPriorityClassManager.getAllPriorityClasses();
    Collections.sort(pClasses, new PriorityClassComparator());
    return pClasses;
  }

  private static class PriorityClassComparator implements Comparator<PriorityClass> {
    public int compare(PriorityClass o1, PriorityClass o2) {
      if (o1.getPriority() > o2.getPriority()) return -1;
      if (o1.getPriority() < o2.getPriority()) return 1;
      return o1.getName().compareTo(o2.getName());      
    }
  }
  
}

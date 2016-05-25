/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.serverSide.priority.controllers.Util.getBuildTypeIds;

/**
 * @author dmitry.neverov
 */
public class DeletePriorityClassController extends BaseFormXmlController {

  private static final PriorityClassNameComparator PRIORITY_CLASS_NAME_COMPARATOR = new PriorityClassNameComparator();
  private final PriorityClassManager myPriorityClassManager;
  private final PluginDescriptor myPluginDescriptor;

  public DeletePriorityClassController(@NotNull final SBuildServer server,
                                       @NotNull final PriorityClassManager pClassManager,
                                       @NotNull final WebControllerManager controllerManager,
                                       @NotNull final PluginDescriptor pluginDescriptor) {
    super(server);
    myPriorityClassManager = pClassManager;
    myPluginDescriptor = pluginDescriptor;
    controllerManager.registerController(myPluginDescriptor.getPluginResourcesPath() + "deletePriorityClassDialog.html", this);
  }

  @Override
  protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
    String priorityClassId = request.getParameter("priorityClassId");
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("deletePriorityClassDialog.jsp"));
    mv.getModel().put("title", "Remove Priority Class");
    mv.getModel().put("priorityClassId", priorityClassId);
    if (priorityClassId != null) {
      PriorityClass pc = myPriorityClassManager.findPriorityClassById(priorityClassId);
      List<PriorityClass> otherPriorityClasses = myPriorityClassManager.getAllPriorityClasses();
      otherPriorityClasses.remove(pc);
      otherPriorityClasses.remove(myPriorityClassManager.getPersonalPriorityClass());
      Collections.sort(otherPriorityClasses, PRIORITY_CLASS_NAME_COMPARATOR);
      if (!pc.getBuildTypes().isEmpty() && otherPriorityClasses.size() > 1) {
        mv.getModel().put("showList", true);
        mv.getModel().put("otherPriorityClasses", otherPriorityClasses);
        mv.getModel().put("configurationCount", pc.getBuildTypes().size());
      } else {
        mv.getModel().put("showList", false);
      }
    }
    return mv;
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
    String priorityClassId = request.getParameter("priorityClassId");
    String moveToId = request.getParameter("moveTo");
    if (priorityClassId != null) {
      PriorityClass pc = myPriorityClassManager.findPriorityClassById(priorityClassId);
      if (pc != null) {
        if (moveToId != null) {
          PriorityClass moveTo = myPriorityClassManager.findPriorityClassById(moveToId);
          if (moveTo != null) {
            Set<String> movedBuildTypes = getBuildTypeIds(pc);
            myPriorityClassManager.savePriorityClass(moveTo.addBuildTypes(movedBuildTypes));
          } else {
            ActionErrors errors = new ActionErrors();
            errors.addError("moveConfigurations", "Selected priority class is no longer exist");
          }
        }

        myPriorityClassManager.deletePriorityClass(pc.getId());
        ActionMessages messages = ActionMessages.getOrCreateMessages(request);
        messages.addMessage("priorityClassDeleted", "Priority Class \"{0}\" has been removed.", pc.getName());
      }
    }
  }

  private static class PriorityClassNameComparator implements Comparator<PriorityClass> {
    public int compare(PriorityClass o1, PriorityClass o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }
}

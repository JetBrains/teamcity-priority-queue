/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.BuildTypeComparator;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.serverSide.priority.exceptions.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * @author dmitry.neverov
 */
public class EditPriorityClassController extends BaseFormXmlController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;
  private final String myDefaultPriorityClassListUrl;

  public EditPriorityClassController(@NotNull final SBuildServer buildServer,
                                     @NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final PriorityClassManager priorityClassManager) {
    super(buildServer);
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = priorityClassManager;
    myDefaultPriorityClassListUrl = myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html";
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "editPriorityClass.html", this);
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    PriorityClass priorityClass = getPriorityClass(request);
    if (priorityClass == null) {
      getOrCreateMessages(request).addMessage("priorityClassNotFound", "Selected priority class does not exist anymore");
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    if (priorityClass.isDefaultPriorityClass()) {
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("editPriorityClass.jsp"));
    EditPriorityClassBean bean = new EditPriorityClassBean(priorityClass);
    bean.getCameFromSupport().setUrlFromRequest(request, myDefaultPriorityClassListUrl);
    bean.getCameFromSupport().setTitleFromRequest(request, "Priority Classes");
    mv.getModel().put("priorityClassBean", bean);
    mv.getModel().put("priorityClass", priorityClass);
    List<SBuildType> sortedBuildTypes = priorityClass.getBuildTypes();
    Collections.sort(sortedBuildTypes, new PriorityQueueBuildTypeComparator());
    mv.getModel().put("sortedBuildTypes", sortedBuildTypes);

    return mv;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    PriorityClass priorityClass = getPriorityClass(request);
    ActionErrors errors = new ActionErrors();
    if (priorityClass == null) {
      ActionMessages.getOrCreateMessages(request).addMessage("priorityClassNotFound", "Selected priority class does not exist anymore");
      errors.addError("priorityClassNotFound", "Selected priority class does not exist anymore");
      //redirect to priority class list?
    } else {
      final EditPriorityClassBean pb = new EditPriorityClassBean();
      FormUtil.bindFromRequest(request, pb);

      try {
        if (myPriorityClassManager.isDefaultPriorityClass(priorityClass)) {
          //do nothing
        } else if (myPriorityClassManager.isPersonalPriorityClass(priorityClass)) {
          PriorityClass updatedPersonal = priorityClass.setPriority(pb.getPriorityClassPriorityInt());
          myPriorityClassManager.savePriorityClass(updatedPersonal);
        } else {
          pb.validate();
          PriorityClass updatedPriorityClass = priorityClass.update(pb.getPriorityClassName(),
                                                                    pb.getPriorityClassDescription(),
                                                                    pb.getPriorityClassPriorityInt());
          myPriorityClassManager.savePriorityClass(updatedPriorityClass);
        }
      } catch (DuplicatePriorityClassNameException e) {
        errors.addError("priorityClassName", e.getMessage());
      } catch (InvalidPriorityClassNameException e) {
        errors.addError("priorityClassName", e.getMessage());
      } catch (InvalidPriorityClassDescriptionException e) {
        errors.addError("priorityClassDescription", e.getMessage());
      } catch (InvalidPriorityClassPriorityException e) {
        errors.addError("priorityClassPriority", e.getMessage());
      } catch (PriorityClassException e) {
        errors.addError("editPriorityClass", e.getMessage());
      }
    }

    errors.serialize(xmlResponse);

    if (errors.hasNoErrors()) {
      ActionMessages.getOrCreateMessages(request).addMessage("priorityClassUpdated", "Priority Class has been updated.");
    }
  }

  @Nullable
  private PriorityClass getPriorityClass(final HttpServletRequest request) {
    String priorityClassId = request.getParameter("priorityClassId");
    if (priorityClassId != null) {
      return myPriorityClassManager.findPriorityClassById(priorityClassId);
    } else {
      return null;
    }
  }
}

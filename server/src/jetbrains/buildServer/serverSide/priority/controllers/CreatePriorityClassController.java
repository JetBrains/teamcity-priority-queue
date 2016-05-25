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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.serverSide.priority.exceptions.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author dmitry.neverov
 */
public class CreatePriorityClassController extends BaseFormXmlController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;
  private final String myDefaultPriorityClassListUrl;

  public CreatePriorityClassController(@NotNull final PluginDescriptor pluginDescriptor,
                                       @NotNull final WebControllerManager manager,
                                       @NotNull final PriorityClassManager priorityClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = priorityClassManager;
    myDefaultPriorityClassListUrl = myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html";
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "createPriorityClass.html", this);
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("createPriorityClass.jsp"));
    EditPriorityClassBean bean = new EditPriorityClassBean();
    bean.getCameFromSupport().setUrlFromRequest(request, myDefaultPriorityClassListUrl);
    bean.getCameFromSupport().setTitleFromRequest(request, "Priority Classes");
    mv.getModel().put("priorityClassBean", bean);
    return mv;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    //create and redirect to editPriorityClass page with message 'created, now you can add configurations'
    ActionErrors errors = new ActionErrors();
    EditPriorityClassBean pcBean = new EditPriorityClassBean();
    FormUtil.bindFromRequest(request, pcBean);

    PriorityClass priorityClass = null;
    try {
      pcBean.validate();
      priorityClass = myPriorityClassManager.createPriorityClass(pcBean.getPriorityClassName(),
              pcBean.getPriorityClassDescription(),
              pcBean.getPriorityClassPriorityInt());
      ActionMessages.getOrCreateMessages(request).addMessage("priorityClassCreated", "Priority Class \"{0}\" has been created, now you can add configurations.",
              priorityClass.getName());
      Element priorityClassId = new Element("priorityClass");
      xmlResponse.addContent((Content) priorityClassId);
      priorityClassId.setAttribute("id", priorityClass.getId());
    } catch (InvalidPriorityClassNameException e) {
      errors.addError("priorityClassName", e.getMessage());
    } catch (DuplicatePriorityClassNameException e) {
      errors.addError("priorityClassName", e.getMessage());
    } catch (InvalidPriorityClassDescriptionException e) {
      errors.addError("priorityClassDescription", e.getMessage());
    } catch (InvalidPriorityClassPriorityException e) {
      errors.addError("priorityClassPriority", e.getMessage());
    } catch (PriorityClassException e) {
      errors.addError("createPriorityClass", e.getMessage());
    }

    if (errors.hasErrors() && priorityClass != null) {
      myPriorityClassManager.deletePriorityClass(priorityClass.getId());
    }
    errors.serialize(xmlResponse);
  }
}

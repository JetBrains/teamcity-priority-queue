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

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassImpl;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.serverSide.priority.exceptions.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author dmitry.neverov
 */
public class EditPriorityClassDialogController extends BaseFormXmlController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;


  public EditPriorityClassDialogController(@NotNull final PluginDescriptor pluginDescriptor,
                                           @NotNull final WebControllerManager manager,
                                           @NotNull final PriorityClassManager priorityClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = priorityClassManager;
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "editPriorityClassDialog.html", this);
  }


  @Override
  protected ModelAndView doGet(HttpServletRequest request, HttpServletResponse response) {
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("editPriorityClassDialog.jsp"));
    String editAction = request.getParameter("editAction");
    if ("update".equals(editAction)) {
      PriorityClass priorityClass = getEditedPriorityClass(request);
      if (priorityClass == null) {
        //do what?
      } else if (priorityClass.isDefault()) {
        //do what?
      } else {
        EditPriorityClassBean pb = new EditPriorityClassBean(priorityClass);
        mv.getModel().put("editPriorityClassBean", pb);
        mv.getModel().put("showDialog", true);
        mv.getModel().put("title", String.format("Edit Priority Class '%s'", priorityClass.getName()));
        mv.getModel().put("submitButtonValue", "Save");
      }
    } else if ("create".equals(editAction)) {
      EditPriorityClassBean pb = new EditPriorityClassBean();
      mv.getModel().put("editPriorityClassBean", pb);
      mv.getModel().put("showDialog", true);
      mv.getModel().put("title", "Create Priority Class");
      mv.getModel().put("submitButtonValue", "Create");
    }
    return mv;
  }


  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response, Element xmlResponse) {
    ActionErrors errors = new ActionErrors();
    String editAction = request.getParameter("editAction");
    doAction(request, xmlResponse, errors, editAction);
  }

  private void doAction(HttpServletRequest request, Element xmlResponse, ActionErrors errors, String editAction) {
    if ("update".equals(editAction)) {
      doUpdate(request, xmlResponse, errors);
    } else if ("create".equals(editAction)) {
      doCreate(request, xmlResponse, errors);
    }
  }

  private void doCreate(HttpServletRequest request, Element xmlResponse, ActionErrors errors) {
    EditPriorityClassBean pcBean = new EditPriorityClassBean();
    FormUtil.bindFromRequest(request, pcBean);

    PriorityClass priorityClass = null;
    try {
      pcBean.validate();
      priorityClass = myPriorityClassManager.createPriorityClass(pcBean.getPriorityClassName(),
              pcBean.getPriorityClassDescription(),
              pcBean.getPriorityClassPriorityInt());
      ActionMessages.getOrCreateMessages(request).addMessage("priorityClassCreated", "Priority Class \"{0}\" has been created.",
              priorityClass.getName());
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

  private void doUpdate(HttpServletRequest request, Element xmlResponse, ActionErrors errors) {
    PriorityClass priorityClass = getEditedPriorityClass(request);
    if (priorityClass == null) {
      ActionMessages.getOrCreateMessages(request).addMessage("priorityClassNotFound", "Selected priority class does not exist anymore");
      errors.addError("priorityClassNotFound", "Selected priority class does not exist anymore");
    } else {
      final EditPriorityClassBean pb = new EditPriorityClassBean();
      FormUtil.bindFromRequest(request, pb);

      try {
        if (myPriorityClassManager.isDefaultPriorityClass(priorityClass)) {
          //do nothing
        } else if (myPriorityClassManager.isPersonalPriorityClass(priorityClass)) {
          pb.validate();
          PriorityClassImpl updatedPersonal = new PriorityClassImpl(priorityClass.getId(), priorityClass.getName(),
                  priorityClass.getDescription(), pb.getPriorityClassPriorityInt(), priorityClass.getBuildTypes());
          myPriorityClassManager.savePriorityClass(updatedPersonal);
        } else {
          pb.validate();
          PriorityClassImpl updatedPriorityClass = new PriorityClassImpl(priorityClass.getId(), pb.getPriorityClassName(),
                  pb.getPriorityClassDescription(), pb.getPriorityClassPriorityInt(), priorityClass.getBuildTypes());
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


  private PriorityClass getEditedPriorityClass(final HttpServletRequest request) {
    String priorityClassId = request.getParameter("priorityClassId");
    if (priorityClassId != null) {
     return myPriorityClassManager.findPriorityClassById(priorityClassId);
    } else {
      return null;
    }
  }
}

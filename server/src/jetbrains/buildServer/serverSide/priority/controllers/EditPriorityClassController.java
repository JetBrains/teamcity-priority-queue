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
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author dmitry.neverov
 */
public class EditPriorityClassController extends BaseFormXmlController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;
  private final String myDefaultPriorityClassListUrl;

  public EditPriorityClassController(@NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final PriorityClassManager priorityClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = priorityClassManager;
    myDefaultPriorityClassListUrl = myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html";
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "editPriorityClass.html", this);
  }

  @Override
  protected ModelAndView doGet(final HttpServletRequest request, final HttpServletResponse response) {
    PriorityClass priorityClass = getPriorityClass(request);
    if (priorityClass == null) {
      getOrCreateMessages(request).addMessage("priorityClassNotFound", "Selected priority class does not exist anymore");
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    if (priorityClass.isDefault()) {
      return new ModelAndView(new RedirectView(myDefaultPriorityClassListUrl, true));
    }
    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("editPriorityClass.jsp"));
    EditPriorityClassBean bean = new EditPriorityClassBean(priorityClass);
    bean.getCameFromSupport().setUrlFromRequest(request, myDefaultPriorityClassListUrl);
    bean.getCameFromSupport().setTitleFromRequest(request, "Priority Classes");
    mv.getModel().put("priorityClassBean", bean);

    return mv;
  }

  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response, final Element xmlResponse) {
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
          pb.validate();
          PriorityClassImpl updatedPersonal = new PriorityClassImpl(myServer.getProjectManager(), priorityClass.getId(), priorityClass.getName(),
                  priorityClass.getDescription(), pb.getPriorityClassPriorityInt(), ((PriorityClassImpl) priorityClass).getBuildTypeIds());
          myPriorityClassManager.savePriorityClass(updatedPersonal);
        } else {
          pb.validate();
          PriorityClassImpl updatedPriorityClass = new PriorityClassImpl(myServer.getProjectManager(), priorityClass.getId(), pb.getPriorityClassName(),
                  pb.getPriorityClassDescription(), pb.getPriorityClassPriorityInt(), ((PriorityClassImpl) priorityClass).getBuildTypeIds());
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

  private PriorityClass getPriorityClass(final HttpServletRequest request) {
    String priorityClassId = request.getParameter("priorityClassId");
    if (priorityClassId != null) {
      return myPriorityClassManager.findPriorityClassById(priorityClassId);
    } else {
      return null;
    }
  }  
}

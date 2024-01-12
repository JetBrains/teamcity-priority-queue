

package jetbrains.buildServer.serverSide.priority.controllers;

import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassImpl;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassPriorityException;
import jetbrains.buildServer.serverSide.priority.exceptions.PriorityClassException;
import jetbrains.buildServer.web.util.CameFromSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class EditPriorityClassBean {
  
  private String myPriorityClassName;
  private String myPriorityClassDescription;
  private String myPriorityClassPriority;
  private String myPriorityClassId;
  private boolean myPersonal;
  private String myEditAction = "create";
  private final CameFromSupport myCameFromSupport = new CameFromSupport();

  EditPriorityClassBean() {
    myPriorityClassName = "";
    myPriorityClassDescription = "";
    myPriorityClassPriority = "0";
    myPriorityClassId = "";
    myPersonal = false;
    myEditAction = "create";
  }

  EditPriorityClassBean(@NotNull final PriorityClass priorityClass) {
    myPriorityClassName = priorityClass.getName();
    myPriorityClassDescription = priorityClass.getDescription();
    myPriorityClassPriority = String.valueOf(priorityClass.getPriority());
    myPriorityClassId = priorityClass.getId();
    myPersonal = priorityClass.isPersonal();    
    myEditAction = "update";
  }

  public String getPriorityClassName() {
    return myPriorityClassName;
  }

  public void setPriorityClassName(String priorityClassName) {
    myPriorityClassName = priorityClassName;
  }

  public String getPriorityClassDescription() {
    return myPriorityClassDescription;
  }

  public void setPriorityClassDescription(String priorityClassDescription) {
    myPriorityClassDescription = priorityClassDescription;
  }

  public int getPriorityClassPriorityInt() throws InvalidPriorityClassPriorityException {
    if (myPriorityClassPriority == null || myPriorityClassPriority.trim().length() == 0) {
      throw new InvalidPriorityClassPriorityException("Priority must be specified");
    } else {
      try {
        int priority = Integer.valueOf(myPriorityClassPriority);
        PriorityClassImpl.checkPriorityIsCorrect(priority);
        return priority; 
      } catch (NumberFormatException e) {
        throw new InvalidPriorityClassPriorityException("Incorrect priority (should be integer value)");
      }
    }
  }

  void validate() throws PriorityClassException {
    PriorityClassImpl.checkNameIsCorrect(myPriorityClassName);
    PriorityClassImpl.checkDescriptionIsCorrect(myPriorityClassDescription);
    getPriorityClassPriorityInt();
  }

  public String getPriorityClassPriority() {
    return myPriorityClassPriority;
  }

  public void setPriorityClassPriority(String priorityClassPriority) {
    myPriorityClassPriority = priorityClassPriority;
  }

  public String getPriorityClassId() {
    return myPriorityClassId;
  }

  public void setPriorityClassId(String priorityClassId) {
    myPriorityClassId = priorityClassId;
  }

  public String getEditAction() {
    return myEditAction;
  }

  public void setEditAction(String editAction) {
    myEditAction = editAction;
  }

  public boolean isPersonal() {
    return myPersonal;
  }

  public void setPersonal(boolean personal) {
    myPersonal = personal;
  }

  public CameFromSupport getCameFromSupport() {
    return myCameFromSupport;
  }
}
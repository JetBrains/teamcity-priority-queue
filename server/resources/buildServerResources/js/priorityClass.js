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

BS.PriorityClassActions = {
  refreshPriorityClassList: function() {
    $('priorityClassList').refresh();
  }
}



BS.EditPriorityClassDialog = OO.extend(BS.AbstractModalDialog, {
  getContainer: function() {
    return $('editPriorityClassDialog');
  },

  getRefreshableContainer: function() {
    return $('editPriorityClassRefreshableContainer');
  },

  showEditDialog: function(priorityClassId, elementToFocus) {
    var that = BS.EditPriorityClassDialog;
    that.elementToFocus = elementToFocus;
    this.getRefreshableContainer().refresh(null, "editAction=update&priorityClassId=" + encodeURIComponent(priorityClassId), function() {
      that.updateDialog();
      that.showCentered();
      BS.EditPriorityClassForm.focusElement(elementToFocus);
    });
  },

  showCreateDialog: function() {
    var that = BS.EditPriorityClassDialog;
    this.getRefreshableContainer().refresh(null, "editAction=create", function() {
      that.updateDialog();
      that.showCentered();
      BS.EditPriorityClassForm.focusFirstElement();
    });
  }
});

BS.EditPriorityClassForm = OO.extend(BS.AbstractWebForm, {
  formElement: function() {
    return $('editPriorityClass');
  },

  reset: function() {
    Form.reset(this.formElement());
    this.clearErrors();
  },

  focusFirstElement: function() {
    Form.focusFirstElement(this.formElement());
  },

  focusElement: function(elementId) {
    $(elementId).activate();
  },

  savingIndicator: function() {
    return $('editPriorityClassProgress');
  },

  submit: function() {
    var that = this;
    BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
      onCreatePriorityClassError: function(elem) {
        $("error_createError").innerHTML = elem.firstChild.nodeValue;
      },

      onPriorityClassNotFound: function() {
        BS.PriorityClassActions.refreshPriorityClassList();
        that.enable();
        BS.EditPriorityClassDialog.close();        
      },

      onPriorityClassNameError: function(elem) {
        $("error_priorityClassName").innerHTML = elem.firstChild.nodeValue;
        that.highlightErrorField($("priorityClassName"));
      },

      onPriorityClassDescriptionError: function(elem) {
        $("error_priorityClassDescription").innerHTML = elem.firstChild.nodeValue;
        that.highlightErrorField($("priorityClassDescription"));
      },

      onPriorityClassPriorityError: function(elem) {
        $("error_priorityClassPriority").innerHTML = elem.firstChild.nodeValue;
        that.highlightErrorField($("priorityClassPriority"));
      },

      onSuccessfulSave: function() {
        BS.PriorityClassActions.refreshPriorityClassList();
        that.enable();
        BS.EditPriorityClassDialog.close();
      }
    }));
    return false;
  }
});



BS.DeletePriorityClassDialog = OO.extend(BS.AbstractModalDialog, {
  getContainer: function() {
    return $('deletePriorityClassDialog');
  },

  getRefreshableContainer: function() {
    return $('deletePriorityClassRefreshableContainer');
  },

  showDeleteDialog: function(priorityClassId, afterFinish) {
    var that = BS.DeletePriorityClassDialog;    
    this.afterFinish = afterFinish;    
    this.getRefreshableContainer().refresh(null, "priorityClassId=" + encodeURIComponent(priorityClassId), function() {
      that.updateDialog();      
      that.showCentered();
    });
  }
});

BS.DeletePriorityClassForm = OO.extend(BS.AbstractWebForm, {
  formElement: function() {
    return $('deletePriorityClass');
  },

  submit: function() {
    var that = this;
    BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
      onMoveConfigurationsError: function(elem) {
        $("error_moveConfigurations").innerHTML = elem.firstChild.nodeValue;
      },

      onSuccessfulSave: function() {
        that.enable();
        BS.DeletePriorityClassDialog.close();
        BS.DeletePriorityClassDialog.afterFinish();
      }
    }, true));
    return false;
  }  
});



BS.UnassignBuildTypesForm = OO.extend(BS.AbstractWebForm, {
  getFormElement: function() {
    return $('unassignBuildTypesForm');
  },

  selectAll: function(select) {
    if (select) {
      BS.Util.selectAll(this.getFormElement(), "unassign");
    } else {
      BS.Util.unselectAll(this.getFormElement(), "unassign");
    }
  },

  selected: function() {
    var checkboxes = Form.getInputs(this.getFormElement(), "checkbox", "unassign");
    for (var i=0; i<checkboxes.length; i++) {
      if (checkboxes[i].checked) {
        return true;
      }
    }

    return false;
  },

  setSaving : function(saving) {
    if (saving) {
      BS.Util.show($('unassignInProgress'));
    } else {
      BS.Util.hide($('unassignInProgress'));
    }
  },

  submit: function() {
    if (!this.selected()) {
      alert("Please select at least one build configuration.");
      return false;
    }

    if (!confirm("Are you sure you want to unassign selected configurations?")) return false;

    BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
      onCompleteSave : function(form, responseXML, errStatus) {
        $('pClassBuildTypesContainer').refresh();
      }
    }));

    return false;
  }
});

BS.AttachConfigurationsToClassDialog = OO.extend(BS.AbstractWebForm, OO.extend(BS.AbstractModalDialog, {
  nonDefaultMovedCount: 0,

  getContainer: function() {
    return $('attachConfigurationsToClassDialog');
  },

  selectAll: function(select) {
    if (select) {
      BS.Util.selectAll(this.formElement(), "configurationId");
    } else {
      BS.Util.unselectAll(this.formElement(), "configurationId");
    }
  },

  selectConfiguration: function(checkbox, isDefaultPriorityClass) {
    if (!isDefaultPriorityClass) {
      if (checkbox.checked) {
        BS.AttachConfigurationsToClassDialog.nonDefaultMovedCount++;
      } else {
        BS.AttachConfigurationsToClassDialog.nonDefaultMovedCount--;        
      }
    }
  },

  showAttachDialog: function(pClassId) {
    var that = BS.AttachConfigurationsToClassDialog;
    this.pClassId = pClassId;
    $('attachConfigurationsToClassContainer').refresh(null, "pClassId=" + encodeURIComponent(pClassId), function() {
      that.showCentered();
      that.focusFirstElement();
    });
  },

  findConfigurations: function() {
    var that = BS.AttachConfigurationsToClassDialog;

    Element.show($('findProgress'));
    var pClassId = this.pClassId;
    var form = this.formElement();

    BS.ajaxRequest(form.action, {
      parameters: "pClassId=" + encodeURIComponent(pClassId) + "&searchString=" + encodeURIComponent(form.searchString.value) + "&searchStringSubmitted=true",
      onSuccess: function() {
        $('configurationListRefreshable').refresh(null, "pClassId=" + encodeURIComponent(pClassId), function() {
          Element.hide($('findProgress'));
          that.updateDialog();
          that.focusFirstElement();
        });
      }
    });
    return false;
  },

  _onSuccess: function() {
    this.nonDefaultMovedCount = 0;
    $('pClassBuildTypesContainer').refresh();
    this.enable();
    this.close();
  },

  formElement: function() {
    return $('attachConfigurationsToClass');
  },

  savingIndicator: function() {
    return $('attachProgress');
  },

  focusFirstElement: function() {
    Form.focusFirstElement(this.formElement());
  },

  submit: function() {
    this.formElement().submitAction.value='assignConfigurations';
    if (this.nonDefaultMovedCount != 0) {
      var msg = "You select " + this.nonDefaultMovedCount + " configuration(s) from non-default priority class, are you sure you want to move them in current priority class?" 
      if (!confirm(msg)) return;      
    }
    var that = this;
    BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
      onAttachToGroupsError: function(elem) {
        $("error_attachToClass_" + that.formElement().id).innerHTML = elem.firstChild.nodeValue;
      },

      onSuccessfulSave: function() {
        that._onSuccess();
      }
    }));
    return false;
  }
}));



BS.PriorityClassConfigurationsPopup = {};
BS.PriorityClassConfigurationsPopup = new BS.Popup("priorityClassConfigurationsPopup", {
  url: base_uri+"/plugins/priority-queue/priorityClassConfigurationsPopup.html",
  method: "get"
});

BS.PriorityClassConfigurationsPopup.showPopup = function(nearestElement, priorityClassId) {
  this.options.parameters = "priorityClassId=" + encodeURIComponent(priorityClassId);
  this.showPopupNearElement(nearestElement);
}


<%--
  ~ Copyright 2000-2010 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@include file="/include.jsp"%>

<c:url var="editAction" value="${teamcityPluginResourcesPath}editPriorityClassDialog.html"/>
<bs:refreshable containerId="editPriorityClassRefreshableContainer" pageUrl="${editAction}">
  <c:if test="${showDialog}">
    <bs:modalDialog formId="editPriorityClass" title="${title}" action="${editAction}"
                    closeCommand="BS.EditPriorityClassDialog.close()" saveCommand="BS.EditPriorityClassForm.submit()">
      <div class="clr"></div>
      <span class="error" id="error_editError"></span>
      <span class="error" id="error_createError"></span>

      <label for="priorityClassName">Name: <l:star/></label>
      <forms:textField name="priorityClassName" id="priorityClassName" value="${editPriorityClassBean.priorityClassName}" style="width:21em" 
                       maxlength="255" disabled="${editPriorityClassBean.personal}"/>
      <span class="error" id="error_priorityClassName"></span>
      <div class="clr spacing"></div>

      <label for="priorityClassDescription">Description:</label>
      <forms:textField name="priorityClassDescription" value="${editPriorityClassBean.priorityClassDescription}" style="width:21em"
                       maxlength="2000" disabled="${editPriorityClassBean.personal}"/>
      <span class="error" id="error_priorityClassName"></span>
      <div class="clr spacing"></div>

      <label for="priorityClassPriority">Priority: <l:star/></label>
      <forms:textField name="priorityClassPriority" id="priorityClassPriority" value="${editPriorityClassBean.priorityClassPriority}" style="width:5em"
                       maxlength="4"/>&nbsp;<span class="grayNote">Integer in interval [-100..100]</span>
      <span class="error" id="error_priorityClassPriority"></span>
      <div class="clr spacing"></div>

      <input type="hidden" name="priorityClassId" value="${editPriorityClassBean.priorityClassId}"/>
      <input type="hidden" name="editAction" value="${editPriorityClassBean.editAction}">
      <br/>

      <div class="popupSaveButtonsBlock">
        <a href="javascript://" onclick="BS.EditPriorityClassDialog.close()" class="cancel">Cancel</a>
        <input class="submitButton" type="submit" value="${submitButtonValue}"/>
        <forms:saving id="editPriorityClassProgress"/>
      </div>
      <br clear="all"/>
    </bs:modalDialog>
  </c:if>
</bs:refreshable>
<%--
~ Copyright 2000-2014 JetBrains s.r.o.
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

<c:url var="action" value="${teamcityPluginResourcesPath}deletePriorityClassDialog.html"/>
<bs:refreshable containerId="deletePriorityClassRefreshableContainer" pageUrl="${action}">
  <bs:modalDialog formId="deletePriorityClass" title="${title}" action="${action}"
                  closeCommand="BS.DeletePriorityClassDialog.close()" saveCommand="BS.DeletePriorityClassForm.submit()">
    <span class="error" id="error_moveConfigurations"></span>
    <c:choose>
      <c:when test="${showList}">
        <div class="moveToPriorityClassContainer">
          <div class="grayNote">
            The selected priority class contains ${configurationCount} configuration<bs:s val="${configurationCount}"/>.
            Please choose another priority class for these configurations.
          </div>
          <br/>
          <label for="moveToPriorityClassSelect" style="width:9em">Priority Class:</label>
          <select name="moveTo" class="priorityClassDropDown" id="moveToPriorityClassSelect">
            <c:forEach items="${otherPriorityClasses}" var="pc">
              <forms:option value="${pc.id}" selected="${pc.defaultPriorityClass}"><c:out value='${pc.name}'/></forms:option>
            </c:forEach>
          </select>
        </div>
      </c:when>
      <c:otherwise>
        Are you sure you want to delete this priority class?
      </c:otherwise>
    </c:choose>

    <br/>

    <input type="hidden" name="priorityClassId" value="<c:out value='${priorityClassId}'/>"/>

    <div class="popupSaveButtonsBlock">
      <forms:cancel onclick="BS.DeletePriorityClassDialog.close()"/>
      <c:choose>
        <c:when test="${showList}">
          <forms:submit label="Apply"/>
        </c:when>
        <c:otherwise>
          <forms:submit label="Delete"/>
        </c:otherwise>
      </c:choose>
      <forms:saving id="deletePriorityClassProgress"/>
    </div>
  </bs:modalDialog>
</bs:refreshable>

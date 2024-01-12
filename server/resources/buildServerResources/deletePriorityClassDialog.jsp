

<%@include file="/include.jsp"%>

<c:url var="action" value="${teamcityPluginResourcesPath}deletePriorityClassDialog.html"/>
<bs:refreshable containerId="deletePriorityClassRefreshableContainer" pageUrl="${action}">
  <bs:modalDialog formId="deletePriorityClass" title="${title}" action="${action}"
                  closeCommand="BS.DeletePriorityClassDialog.close()" saveCommand="BS.DeletePriorityClassForm.submit()">
    <span class="error" id="error_moveConfigurations"></span>
    <c:choose>
      <c:when test="${showList}">
        <div class="moveToPriorityClassContainer">
          <bs:smallNote>
            The selected priority class contains ${configurationCount} configuration<bs:s val="${configurationCount}"/>.
            Please choose another priority class for these configurations.
          </bs:smallNote>
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
      <c:choose>
        <c:when test="${showList}">
          <forms:submit label="Apply"/>
        </c:when>
        <c:otherwise>
          <forms:submit label="Delete"/>
        </c:otherwise>
      </c:choose>
      <forms:cancel onclick="BS.DeletePriorityClassDialog.close()"/>
      <forms:saving id="deletePriorityClassProgress"/>
    </div>
  </bs:modalDialog>
</bs:refreshable>
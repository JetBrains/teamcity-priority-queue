

<%@ include file="/include.jsp" %>

<authz:authorize allPermissions="REORDER_BUILD_QUEUE">
  <c:url var="prioritiesUrl" value="${teamcityPluginResourcesPath}priorityClassList.html"/>
  <ring:link href="${prioritiesUrl}">Priorities</ring:link>
</authz:authorize>


<%@ include file="/include.jsp" %>

<authz:authorize allPermissions="REORDER_BUILD_QUEUE">
  <c:url var="prioritiesUrl" value="${teamcityPluginResourcesPath}priorityClassList.html"/>
  <script type="text/javascript">
    $j('.quickLinks').prepend('<a class="quickLinksControlLink" href="${prioritiesUrl}">Configure Build Priorities</a>');
  </script>
</authz:authorize>
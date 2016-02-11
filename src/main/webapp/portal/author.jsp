<%@ include file="include.jsp"%>
<!--[if lt IE 7]>      <html lang="en" dir="${textDirection}" class="no-js lt-ie9 lt-ie8 lt-ie7"> <![endif]-->
<!--[if IE 7]>         <html lang="en" dir="${textDirection}" class="no-js lt-ie9 lt-ie8"> <![endif]-->
<!--[if IE 8]>         <html lang="en" dir="${textDirection}" class="no-js lt-ie9"> <![endif]-->
<!--[if gt IE 8]><!--> <html dir="${textDirection}" class="no-js" lang="en"> <!--<![endif]-->
<head>
    <meta charset="utf-8">
    <meta http-equiv="x-ua-compatible" content="ie=edge">
    <title>WISE Authoring</title>
    <base href="${contextPath}/" target="_blank">
    <meta name="description" content="WISE Authoring Tool">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="shortcut icon" href="${contextPath}/<spring:theme code="favicon"/>" />
    <link rel="apple-touch-icon" href="apple-touch-icon.png">

    <script src="${contextPath}/wise5/jspm_packages/system.js"></script>
    <script src="${contextPath}/wise5/config.js"></script>
    <script>
        System.import('${contextPath}/wise5/authoringTool/bootstrap');
    </script>
</head>
<body>
    <div ng-view>
        <div ui-view></div>
    </div>
    <script>
    var configURL = '${configURL}';
    </script>
</body>
</html>
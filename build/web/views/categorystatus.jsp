<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<title>SKS Category Status</title>
		<link rel="stylesheet" type="text/css" href="theme.css"/>
	</head>
	<body>
		<h2>Load status for category <span class="category">${requestScope['category']}</span></h2>
		<h4>Status: Loaded</h4>
		<h4>Last updated on: ${requestScope['updatedOn']}</h4>
	</body>
</html>

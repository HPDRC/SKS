<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<title>SKS Loading</title>
		<link rel="stylesheet" type="text/css" href="theme.css"/>
	</head>
	<body>
		<h1>Category <span class="category">${requestScope['category']}</span> has started loading</h1>
		<p>
			<a href="/sks/status?category=${requestScope['category']}">View loading status</a><br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=2">View loading status</a> with automatic 2 seconds refresh<br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=10">View loading status</a> with automatic 10 seconds refresh<br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=30">View loading status</a> with automatic 30 seconds refresh<br/>
		</p>
	</body>
</html>

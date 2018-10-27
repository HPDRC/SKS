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
		<h1>Category <span class="category">${requestScope['category']}</span> is not ready</h1>
		<p>
			<a href="/sks/status?category=${requestScope['category']}">Check Status</a>
		</p>
	</body>
</html>

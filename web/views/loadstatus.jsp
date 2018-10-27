<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<c:if test="${param['refresh'] ne null}">
			<meta http-equiv="refresh" content="${param['refresh']}">
		</c:if>
		<title>SKS Loading Status</title>
		<link rel="stylesheet" type="text/css" href="theme.css"/>
	</head>
	<body>
		<h1>Load status for category <span class="category">${requestScope['category']}</span></h1>
		<c:choose>
			<c:when test="${requestScope['downloading']}">
				<h4>Currently downloading.</h4>
				<table class="status" border="1" cellspacing="2" cellpadding="2">
					<tr>
						<th>remote</th>
						<td>${requestScope['remote']}</td>
					</tr>
					<tr>
						<th>local</th>
						<td>${requestScope['local']}</td>
					</tr>
					<tr>
						<th>bytes downloaded</th>
						<td>${requestScope['bytes']}</td>
					</tr>
				</table>
			</c:when>
			<c:otherwise>
				<h4>Currently building index.</h4>
				<table class="status" border="1" cellspacing="2" cellpadding="2">
					<tr>
						<th>Stage</th>
						<td>${requestScope['stage']}</td>
					</tr>
					<tr>
						<th>Records processed</th>
						<td>${requestScope['processed']}</td>
					</tr>                                        
				</table>
			</c:otherwise>
		</c:choose>
		<p>
			<a href="/sks/status?category=${requestScope['category']}">Refresh</a><br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=2">Refresh</a> with automatic 2 seconds refresh</a><br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=10">Refresh</a> with automatic 10 seconds refresh</a><br/>
			<a href="/sks/status?category=${requestScope['category']}&refresh=30">Refresh</a> with automatic 30 seconds refresh</a><br/>
		</p>
	</body>
</html>

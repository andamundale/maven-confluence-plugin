"use strict";
var confluence_xmlrpc_1 = require("./confluence-xmlrpc");
var config = require("./config.json").softphone;
var confluence;
confluence_xmlrpc_1.XMLRPCConfluenceService.create(config, config.credentials)
    .then(function (c) { return confluence = c; })
    .then(function () {
    console.log("logged in");
    return confluence.getPage(config.spaceId, config.pageTitle);
})
    .then(function (value) { return console.log("page", value); })
    .then(function () {
    return confluence.getOrCreatePage(config.spaceId, config.pageTitle, "mytitle")
        .then(function (result) { return console.log("mytitle", result); });
})
    .then(function () { return confluence.connection.logout(); })
    .then(function (value) { return console.log("logged out", value); })
    .catch(function (error) {
    console.log("error", error);
    return confluence.connection.logout();
});
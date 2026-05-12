var exec = require('cordova/exec');

exports.openPdfUsingSAF = function (success, error) {
    cordova.exec(success, error, "OpenFile", "openPdfUsingSAF", []);
};

exports.openDownloadedFile = function (filePath, optionsOrSuccess, successOrError, error) {
    var options, successCb, errorCb;
    if (typeof optionsOrSuccess === 'function') {
        // Old signature: openDownloadedFile(filePath, success, error)
        options = {};
        successCb = optionsOrSuccess;
        errorCb = successOrError;
    } else {
        // New signature: openDownloadedFile(filePath, options, success, error)
        options = optionsOrSuccess || {};
        successCb = successOrError;
        errorCb = error;
    }
    cordova.exec(successCb, errorCb, "OpenFile", "openDownloadedFile", [filePath, options]);
};

exports.requestPermissions = function (success, error) {
    cordova.exec(success, error, "OpenFile", "requestStoragePermissions", []);
};

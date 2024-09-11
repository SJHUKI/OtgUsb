var exec = require('cordova/exec');

exports.getUsbPath = function (success, error) {
    exec(success, error, 'OtgUsb', 'getUsbPath');
};

exports.moveToUSB = function (arg0, arg1, arg2, success, error) {
    exec(success, error, 'OtgUsb', 'moveToUSB', [arg0, arg1, arg2]);
};

exports.getPermission = function (success, error) {
    exec(success, error, 'OtgUsb', 'getPermission');
};

// 测试修改

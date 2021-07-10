const path = require('path');
const webpack = require('webpack');

const backendUrl = process.env.BACKEND_URL || 'http://localhost:9090';
const fullBackEndUrl = (backendUrl.endsWith("/") ? backendUrl : backendUrl + "/") + "api/v1";

module.exports = {
    outputDir: path.resolve(__dirname, 'build/web'),
    configureWebpack: {
        plugins: [
            new webpack.DefinePlugin({ BACKEND_URL: `"${fullBackEndUrl}"` })
        ]
    }
};

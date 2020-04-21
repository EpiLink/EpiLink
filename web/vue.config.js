const path = require('path');
const webpack = require('webpack');

const backendURL = process.env.BACKEND_URL || 'http://localhost:9090/api/v1';

module.exports = {
    outputDir: path.resolve(__dirname, '../build/web'),
    configureWebpack: {
        plugins: [
            new webpack.DefinePlugin({ BACKEND_URL: `"${backendURL}"` })
        ]
    }
};

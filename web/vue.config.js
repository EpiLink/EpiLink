const path = require('path');
const webpack = require('webpack');

if (!process.env.BACKEND_URL) {
    throw 'Missing "BACKEND_URL" environment variable';
}

module.exports = {
    outputDir: path.resolve(__dirname, '../build/web'),
    configureWebpack: {
        plugins: [
            new webpack.DefinePlugin({ BACKEND_URL: `"${process.env.BACKEND_URL}"` })
        ]
    }
};

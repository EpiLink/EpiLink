const path = require('path');

module.exports = (env, argv) => ({
    mode: argv.mode,
    entry: "./src/main.tsx",
    resolve: {
        extensions: [".ts", ".tsx", ".js"],
        alias: {
            environment: path.join(__dirname, 'src', 'environment', `environment${env.prod === 'true' ? 'prod' : 'dev'}.ts`)
        }
    },
    module: {
        rules: [
            {
                test: /\.ts(x?)$/,
                exclude: /node_modules/,
                use: [
                    {
                        loader: "ts-loader"
                    }
                ]
            },
            {
                test: /\.css$/,
                exclude: /node_modules/,
                use: [
                    { loader: "style-loader" },
                    {
                        loader: "css-loader",
                        query: {
                            modules: true
                        }
                    }
                ]
            },
            {
                test: /\.s[ac]ss$/,
                exclude: /node_modules/,
                use: [
                    "style-loader",
                    "css-loader",
                    "sass-loader"
                ]
            }
        ],
    },
    devServer: {
        historyApiFallback: true
    }
});
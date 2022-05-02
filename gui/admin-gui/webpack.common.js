/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

const webpack = require('./node_modules/webpack');
const MiniCssExtractPlugin = require('./node_modules/mini-css-extract-plugin');

const path = require('path');

module.exports = {
    entry: {
        vendors: [
            './src/frontend/js/vendors.js',
            './src/frontend/scss/vendors.scss',
        ],
        midpoint: [
            './src/frontend/js/index.js',
            './src/frontend/scss/midpoint-theme.scss',
        ],
    },
    output: {
        path: path.resolve(__dirname, 'target/generated-resources/webpack/static/static'),
        publicPath: '../static/',
        filename: './[name].bundle.js',
    },
    module: {
        // noParse: /midpoint-theme.js/,
        rules: [
            {
                test: /\.(sass|scss|css)$/,
                use: [
                    MiniCssExtractPlugin.loader,
                    {
                        loader: './node_modules/css-loader',
                        options: {
                            importLoaders: 2,
                            sourceMap: false,
                            modules: false,
                        },
                    },
                    './node_modules/postcss-loader',
                    './node_modules/sass-loader',
                ],
            },
            // {
            //     test: require.resolve('../../../../node_modules/jquery'),
            //     use: [{
            //         loader: './node_modules/expose-loader',
            //         options: 'jQuery'
            //     },
            //         {
            //             loader: './node_modules/expose-loader',
            //             options: '$'
            //         }
            //     ]
            // },
            {
                test: /\.js$/,
                exclude: /node_modules/,
                // type: 'asset/resource',
                use: {
                    loader: "./node_modules/babel-loader"
                }
            },

            // Images: Copy image files to build folder
            { test: /\.(?:ico|gif|png|jpg|jpeg)$/i, type: 'asset/resource' },

            // Fonts and SVGs: Inline files
            { test: /\.(woff(2)?|eot|ttf|otf|svg|)$/, type: 'asset/inline' },
        ],
    },
    plugins: [
        new webpack.ProvidePlugin({
            $: 'jquery',
            jQuery: 'jquery',
        }),
    ],
    externals: {
        // external (from webjar) not to collide with wicket headers.
        jquery: 'jQuery',
    },
}
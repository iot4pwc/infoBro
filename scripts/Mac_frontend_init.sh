#!/bin/bash

# https://blog.madewithenvy.com/getting-started-with-webpack-2-ed2b86c68783

# install yarn package control
# override hadooop yarn
brew link --overwrite yarn
brew install yarn
brew upgrade yarn
mkdir ../web && cd ../web
# install dependencies
yarn init --yes
yarn add --dev webpack webpack-dev-server babel-core babel-loader babel-preset-react babel-preset-es2015 \
eslint flow css-loader less less-loader html-webpack-plugin source-map extract-text-webpack-plugin redux-devtools
yarn add react react-dom react-router redux redux-thunk redux-promise react-redux
yarn install

echo 'const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
    context: path.resolve('src'),
    entry: {
        app: './index.js',
    },
    output: {
        path: path.resolve('dist', 'app'),
        filename: '[name].bundle.js',
        publicPath: 'js',
    },
    devServer: {
        contentBase: path.resolve('dist', 'app'),
    },
    devtool: 'source-map',
    // add transpile rules
    module: {
        rules: [
            {
                // if separate css bundles are needed, set the use object to get these css files
                test: /\.jsx?/,
                exclude: [/node_modules/],
                use: [{
                    loader: 'babel-loader',
                    options: { presets: ['react', 'es2015'] },
                }],
            },
            {
                test: /\.less$/,
                use:
                // css modules, make class local
                // see https://github.com/css-modules/css-modules
          ExtractTextPlugin.extract({
              use : [{
                  loader: 'css-loader',
                  options: { modules: true, importLoaders: 1 },
              }, 'less-loader'],
          }),
            },
        ],
    },
    // remove duplicate dependencies
    plugins: [
        new webpack.optimize.CommonsChunkPlugin({
            name: 'commons',
            filename: 'commons.js',
            minChunks: 2,
        }),
        new HtmlWebpackPlugin({
            title: 'information broadcaster',
            template: '../public/index.tpl.html',
        }),
        new ExtractTextPlugin({
            filename: '[name].bundle.css',
            allChunks: true,
        }),    
    ],
};
' >> webpack.config.js

mkdir -p dist/app
mkdir -p src/components && mkdir -p src/styles && cd src

# the html template for webpack html-loader
echo '<!DOCTYPE html>
<html lang=en>
  <head>
    <title>Information Broadcaster</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta.2/css/bootstrap.min.css" integrity="sha384-PsH8R72JQ3SOdhVi3uxftmaW6Vc51MKb0q5P2rRUpPvrszuE4W1povHYgTpBfshb" crossorigin="anonymous">
  </head>
  <body>
    <div id="app"></div>
  </body>
  <script src="https://code.jquery.com/jquery-3.2.1.slim.min.js" integrity="sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN" crossorigin="anonymous" />
  <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.3/umd/popper.min.js" integrity="sha384-vFJXuSJphROIrBnz7yo7oB41mKfc8JzQZiCq4NCceLEaO4IHwicKwpJf9c9IpFgh" crossorigin="anonymous" />
  <script src="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta.2/js/bootstrap.min.js" integrity="sha384-alpBpkh1PFOepccYVYDB4do5UnbKysX5WZXm3XxPqe5iKTfUKjNkCk9SaVuEZflJ" crossorigin="anonymous" />
</html>
' >> ../public/index.tpl.html
touch index.js

# TODO: set up Eslint


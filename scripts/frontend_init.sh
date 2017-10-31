#!/bin/bash

# https://blog.madewithenvy.com/getting-started-with-webpack-2-ed2b86c68783

# install yarn package control
# override hadooop yarn
brew link --overwrite yarn
brew install yarn
brew upgrade yarn
mkdir web && cd web
# install dependencies
yarn init --yes
yarn add --dev webpack babel-core babel-loader babel-preset-react babel-preset-es2015 \
eslint flow css-loader less less-loader html-webpack-plugin source-map extract-text-webpack-plugin
yarn add react react-dom react-router redux redux-thunk redux-promise react-redux 
yarn install

echo "const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
  context: path.resolve(__dirname, 'src', 'components'),
  entry: {
    app: './index.js',
  },
  output: {
    path: path.resolve(__dirname, 'dist', 'app'),
    filename: '[name].bundle.js',
    publicPath: '/app',
  },
  devServer: {
    contentBase: path.resolve(__dirname, 'dist'),
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
        use: [
          {
            // css modules, make class local
            // see https://github.com/css-modules/css-modules
            ExtractTextPlugin.extract({
              use : [{
                loader: 'css-loader',
                options: { modules: true, importLoaders: 1 },
              }],
            })
          },
          'less-loader',
        ],
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
      template: './index.tpl.html',
    }),
    new ExtractTextPlugin({
      filename: '[name].bundle.css',
      allChunks: true,
    }),    
  ],
};" >> webpack.config.js

mkdir -p dist/app
mkdir -p src/components && mkdir -p src/styles && cd src

# the html template for webpack html-loader
echo "<\!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
  </head>
  <body>
    <div id="app"></div>
  </body>
</html>" >> index.tpl.html

# TODO: set up Eslint


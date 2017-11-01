const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
  context: path.resolve(__dirname, 'src'),
  entry: {
    app: './index.js',
  },
  output: {
    path: path.resolve(__dirname, 'dist', 'app'),
    filename: '[name].bundle.js',
    publicPath: 'js',
  },
  devServer: {
    contentBase: path.resolve(__dirname, 'dist', 'app'),
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
      template: './index.tpl.html',
    }),
    new ExtractTextPlugin({
      filename: '[name].bundle.css',
      allChunks: true,
    }),    
  ],
};

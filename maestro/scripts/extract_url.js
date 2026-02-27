function getUrl(text) {
  var start = text.indexOf('https');
  return text.substr(start);
}

output.utils = {
  getUrl: getUrl
}

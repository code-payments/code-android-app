const path = require('path');
const fs = require('fs');

const directoryPath = 'strings_temp/';

fs.readdir(directoryPath, function(err, files) {
    if (err) {
        return console.log('Unable to scan directory: ' + err);
    }

    files.forEach(function(file) {
        console.log(file);
        openFile(directoryPath + file + "/strings.xml", directoryPath + file + "/strings-localized.xml");
    });
});

function openFile(filePath, targetPath) {
    fs.rename(filePath, targetPath, function(err) {
        if (err) {
            console.log('ERROR: ' + err);
        } else {
            fs.readFile(targetPath, {
                encoding: 'utf-8'
            }, function(err, data) {
                if (!err) {
                    let lines = data.split(/\r?\n/);
                    let linesFiltered = [];
                    lines.forEach(function(part, index, array) {
                        let line = lines[index];
                        if (line.indexOf("<?xml") != -1) {
                            linesFiltered.push(line);
                        } else if (line.indexOf("><") == -1) {
                            let lineArr = line.split('">');
                            lineArr[0] = lineArr[0].replace(new RegExp("\\.", 'g'), "_");
                            if (lineArr.length > 1) lineArr[1] = lineArr[1].replace(new RegExp("%@", 'g'), "%s");
                            let lineNew = lineArr.join('">');
                            linesFiltered.push(lineNew);
                        }
                    });
                    if (linesFiltered.length <= 4) {
                        fs.unlink(targetPath, err => {
                            console.log('Deleted ' + targetPath);
                        })
                    } else {
                        let content = linesFiltered.join("\r\n");
                        fs.writeFile(targetPath, content, err => {
                            if (!err) {
                                console.log('Wrote ' + targetPath);
                            } else {
                                console.log(err);
                            }
                        })
                    }
                } else {
                    console.log(err);
                }
            });
        }
    });
}
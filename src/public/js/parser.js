
/* Parses a feature, that is, checks whether the current line represents a
 * show feature.
 */
var parseFeature = function (line, featureStatus) {
    if (line.search(/Sample Mania/i) !== -1) {
        featureStatus['sample-mania'] = true;
    } else if (line.search(/Does It Sound Good at \d+\?/i) !== -1) {
        featureStatus['sound-good'] = true;
    } else if (line.search(/(?:The )?Hardest Record In The World/i) !== -1) {
        featureStatus['hardest-record'] = true;
    } else if (line.search(/[\w\s]+ Guest Mix/i) !== -1) {
        featureStatus['guest-mix'] = true;
    } else if (line.search(/Final Vinyl/i) !== -1) {
        featureStatus['final-vinyl'] = true;
    }

    return featureStatus;
};

// Parses a track extracting artist and track name including possible show feature
var parseTrack = function (trackLine, feature) {
    var split = trackLine.split(/\s+[-–]\s+/);
    if (split.length !== 2) {
        // Invalid track name
        return null;
    }

    return {'artist': split[0].trim(),
            'track': split[1].trim(),
            'feature': feature ? feature : null};
};

// Parses a KTRA tracklist and returns it as an array
var parseTracklist = function (tracklistArray) {
    var features = {'sound-good': false,
                    'hardest-record': false,
                    'sample-mania': false,
                    'guest-mix': false,
                    'final-vinyl': false},
        track = null,
        tracks = [];

    for (var i = 0; i < tracklistArray.length;) {        
        features = parseFeature(tracklistArray[i], features);
        if (features['sound-good']) {
            i++;
            track = parseTrack(tracklistArray[i++], 'sound-good');
            if (track) {
                tracks.push(track);
            }
            features['sound-good'] = false;
        } else if (features['hardest-record']) {
            i++;
            track = parseTrack(tracklistArray[i++], 'hardest-record');
            if (track) {
                tracks.push(track);
            }
            features['hardest-record'] = false;
        } else if (features['sample-mania']) {
            i++;
            track = parseTrack(tracklistArray[i++], 'sample-mania');
            if (track) {
                tracks.push(track);
            }

            track = parseTrack(tracklistArray[i++], 'sample-mania');
            if (track) {
                tracks.push(track);
            }
            features['sample-mania'] = false;
        } else if (features['final-vinyl']) {
            features['guest-mix'] = false;
            i++;
            track = parseTrack(tracklistArray[i++], 'final-vinyl');
            if (track) {
                tracks.push(track);
            }
            features['final-vinyl'] = false;
        } else if (features['guest-mix']) {
            track = parseTrack(tracklistArray[i++], 'guest-mix');
            if (track) {
                tracks.push(track);
            }
        } else {
            // Normal tracks
            track = parseTrack(tracklistArray[i], null);
            if (track) {
                tracks.push(track);
            }
            i++;
        }
    }
    return tracks;
};

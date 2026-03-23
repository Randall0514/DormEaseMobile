exports.up = (pgm) => {
    pgm.addColumns('dorms', {
        latitude: { type: 'double precision', default: null },
        longitude: { type: 'double precision', default: null },
    });
};

exports.down = (pgm) => {
    pgm.dropColumns('dorms', ['latitude', 'longitude']);
};
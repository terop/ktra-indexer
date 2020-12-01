
var addTablesorter = function (tableId) {
    // Add a hack to sort Finnish style dates
    $.tablesorter.addParser({
        id: 'fidate',
        is: function(s) {
            return false;
        },
        format: function(s, table) {
            s = s.replace(/\-/g, '.');
            s = s.replace(/(\d{1,2})\.(\d{1,2})\.(\d{4})/, '$3/$2/$1');
            return $.tablesorter.formatFloat(new Date(s).getTime());
        },
        type: 'numeric'
    });

    $(tableId).tablesorter({
        theme: 'bootstrap',
        usNumberFormat: 'false',
        dateFormat: 'dd.mm.yyyy',
        headers: {
            2: { sorter: 'fidate' }
        }
    });
};

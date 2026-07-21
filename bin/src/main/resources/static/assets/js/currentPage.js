document.getElementById('currentPage').addEventListener('input', function () {
    let enteredPage = parseInt(this.value);
    let totalPages = parseInt(document.querySelector('span:last-child').textContent.split('/')[1].trim());

    if (enteredPage < 1) {
        this.value = 1;
    } else if (enteredPage > totalPages) {
        this.value = totalPages;
    }
});

document.getElementById('currentPage').addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
        let enteredPage = parseInt(this.value);
        let totalPages = parseInt(document.querySelector('span:last-child').textContent.split('/')[1].trim());

        if (enteredPage < 1) {
            enteredPage = 1;
        } else if (enteredPage > totalPages) {
            enteredPage = totalPages;
            this.value = totalPages;
        }

        window.location.href = '/owner/manageStores?page=' + (enteredPage - 1) + '&size=' + document.getElementById('recordsPerPage').value;
    }
});

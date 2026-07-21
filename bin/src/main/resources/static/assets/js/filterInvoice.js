function filterInvoice() {
    let filterFromId = document.getElementById("filterFromId").value.toLowerCase();
    let filterToId = document.getElementById("filterToId").value.toLowerCase();
    let filterName = document.getElementById("filterName").value.toLowerCase();
    let filterPhone = document.getElementById("filterPhone").value.toLowerCase();
    let filterFromAmount = document.getElementById("filterFromAmount").value.toLowerCase();
    let filterToAmount = document.getElementById("filterToAmount").value.toLowerCase();
    let filterStatus = document.getElementById("filterStatus").value.toLowerCase();
    let filterCreatedBy = document.getElementById("filterCreatedBy").value.toLowerCase();

    let filterFromCreated = document.getElementById("filterFromCreated").value;
    let filterToCreated = document.getElementById("filterToCreated").value;
    let filterFromUpdated = document.getElementById("filterFromUpdated").value;
    let filterToUpdated = document.getElementById("filterToUpdated").value;

    let rows = document.querySelectorAll("#tableBody tr");

    let fromCreatedDate = filterFromCreated ? new Date(filterFromCreated) : null;
    let toCreatedDate = filterToCreated ? new Date(filterToCreated) : null;
    let fromUpdatedDate = filterFromUpdated ? new Date(filterFromUpdated) : null;
    let toUpdatedDate = filterToUpdated ? new Date(filterToUpdated) : null;

    let totalFiltered = 0;

    rows.forEach(row => {
        let id = row.cells[0].innerText.toLowerCase();
        let name = row.cells[1].innerText.toLowerCase();
        let phone = row.cells[2].innerText.toLowerCase();
        let amount = row.cells[3].innerText.replace(/[^0-9]/g, "");
        let status = row.cells[4].innerText.toLowerCase();
        let createdBy = row.cells[5].innerText.toLowerCase();
        let createdAt = row.cells[6].innerText;
        let updatedAt = row.cells[7].innerText;

        let createdAtDate = new Date(createdAt);
        let updatedAtDate = new Date(updatedAt);

        let match = true;

        if (filterFromId && parseInt(id) < parseInt(filterFromId)) match = false;
        if (filterToId && parseInt(id) > parseInt(filterToId)) match = false;
        if (filterName && !name.includes(filterName)) match = false;
        if (filterPhone && !phone.includes(filterPhone)) match = false;
        if (filterFromAmount && BigInt(amount) < BigInt(filterFromAmount)) match = false;
        if (filterToAmount && BigInt(amount) > BigInt(filterToAmount)) match = false;
        if (filterStatus && !status.includes(filterStatus)) match = false;
        if (filterCreatedBy && !createdBy.includes(filterCreatedBy)) match = false;

        if (fromCreatedDate && createdAtDate < fromCreatedDate) match = false;
        if (toCreatedDate && createdAtDate > toCreatedDate) match = false;
        if (fromUpdatedDate && updatedAtDate < fromUpdatedDate) match = false;
        if (toUpdatedDate && updatedAtDate > toUpdatedDate) match = false;

        if (match) {
            row.style.display = '';
            totalFiltered++;
        } else {
            row.style.display = 'none';
        }
    });

    document.querySelector(".total-stores").textContent = `Tổng cộng: ${totalFiltered} hóa đơn`;
}

document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll('input').forEach(input => {
        input.addEventListener('input', filterInvoice);
    });

    const clearButton = document.getElementById("clearFilters");
    clearButton.addEventListener("click", function () {
        document.querySelectorAll("tr.bg-gray-50 input").forEach(input => {
            input.value = "";
        });

        let rows = document.querySelectorAll("#tableBody tr");
        rows.forEach(row => {
            row.style.display = '';
        });

        filterInvoice();
    });
});
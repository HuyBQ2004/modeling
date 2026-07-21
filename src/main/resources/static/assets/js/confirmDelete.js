let storeIdToDelete = null;

function showConfirmModal(storeId) {
    storeIdToDelete = storeId;
    document.getElementById('confirmationModal').classList.remove('hidden');
}

document.getElementById('confirmDelete').onclick = function () {
    window.location.href = '/deleteStore/' + storeIdToDelete;
};

document.getElementById('cancelDelete').onclick = function () {
    document.getElementById('confirmationModal').classList.add('hidden');
};
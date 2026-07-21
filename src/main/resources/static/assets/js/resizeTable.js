document.addEventListener("DOMContentLoaded", function () {
    const resizers = document.querySelectorAll(".resizer");

    resizers.forEach(resizer => {
        let isResizing = false;
        let startX, startWidth, column;

        resizer.style.position = "absolute";
        resizer.style.right = "0";
        resizer.style.top = "0";
        resizer.style.width = "5px";
        resizer.style.height = "100%";
        resizer.style.cursor = "col-resize";

        resizer.addEventListener("mousedown", function (event) {
            isResizing = true;
            column = resizer.parentElement;
            startX = event.clientX;
            startWidth = column.offsetWidth;

            document.addEventListener("mousemove", handleMouseMove);
            document.addEventListener("mouseup", handleMouseUp);
        });

        function handleMouseMove(event) {
            if (!isResizing) return;
            column.style.width = (startWidth + (event.clientX - startX)) + "px";
        }

        function handleMouseUp() {
            isResizing = false;
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        }
    });
});

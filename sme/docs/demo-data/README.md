# Chapter Bookshop demo catalogue

`chapter-books.json` contains 15 sample paperback products in five genres. The titles and authors refer to classic works; prices, stock, editions and cover art are demonstration data, not verified retail offers. Original vector sources live in the frontend at `public/demo-books/`. The catalogue and template reference PNG renditions uploaded to the shared media bucket, so images are independent of the frontend deployment. The 16 assets are also registered in the workspace media library.

The `chapter-bookshop` template is registered through the backend template seed loader. Its frontend renderer provides book covers, a working catalogue search form, editable sections, navigation, cart and order tracking. Deploy the matching frontend assets and renderer with the backend template addition.

The former Mobile Hub template (`artisan-atelier`) has status `DISABLED`: it is excluded from the gallery and cannot be newly selected. Its renderer and historical configurations are retained so existing storefronts remain readable.

The requested catalogue replacement for lukeshisouth@gmail.com was applied as a scoped transaction after backing up the previous rows outside the repository. Historical order snapshots were preserved; their nullable references to deleted products were cleared. Chapter Bookshop was applied to the workspace draft, without modifying its published snapshot. This JSON file is documentation/data, not an automatic destructive startup migration.

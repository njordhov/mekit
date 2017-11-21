var loaded_messenger_extensions_sdk = false;
(function(d, s, id){
      var js, fjs = d.getElementsByTagName(s)[0];
      if (d.getElementById(id)) {return;}
      window.extAsyncInit = function() {
        console.log("Messenger Extensions JS SDK is done loading");
        loaded_messenger_extensions_sdk = true;
      };
      js = d.createElement(s); js.id = id;
      js.src = "//connect.facebook.com/en_US/messenger.Extensions.js";
      fjs.parentNode.insertBefore(js, fjs);
    }(document, "script", "Messenger"));

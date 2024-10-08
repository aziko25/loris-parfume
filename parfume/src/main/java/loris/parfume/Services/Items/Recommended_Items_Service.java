package loris.parfume.Services.Items;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import loris.parfume.DTOs.Requests.Items.RecommendedItemsRequest;
import loris.parfume.DTOs.returnDTOs.Recommended_Items_DTO;
import loris.parfume.Models.Items.*;
import loris.parfume.Models.Items.Collections;
import loris.parfume.Repositories.Items.CollectionsRepository;
import loris.parfume.Repositories.Items.ItemsRepository;
import loris.parfume.Repositories.Items.Recommended_Items_Repository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Recommended_Items_Service {

    private final Recommended_Items_Repository recommendedItemsRepository;
    private final ItemsRepository itemsRepository;

    private final CollectionsRepository collectionsRepository;

    @Transactional
    public List<Recommended_Items_DTO> create(RecommendedItemsRequest recommendedItemsRequest) {

        Items item = itemsRepository.findById(recommendedItemsRequest.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item " + recommendedItemsRequest.getItemId() + " Not Found"));

        return setRecommendedItems(recommendedItemsRequest, item);
    }

    public List<Recommended_Items_DTO> all() {

        List<Recommended_Items> recommendedItemsPage = recommendedItemsRepository.findAll();

        Map<Items, List<Items>> groupedItems = recommendedItemsPage.stream()
                .collect(Collectors.groupingBy(
                        Recommended_Items::getItem,
                        Collectors.mapping(Recommended_Items::getRecommendedItem, Collectors.toList())
                ));

        return groupedItems.values().stream()
                .map(Recommended_Items_DTO::new)
                .collect(Collectors.toList());
    }

    public Recommended_Items_DTO getById(Long itemId) {

        List<Collections> allCollections = collectionsRepository.findAll();

        List<Items> recommendedItemsList = allCollections.stream()
                .map(collection -> {
                    List<Items> collectionItems = collection.getCollectionsItemsList().stream()
                            .map(Collections_Items::getItem)
                            .toList();

                    if (collectionItems.isEmpty()) {

                        return null;
                    }

                    return collectionItems.get(new Random().nextInt(collectionItems.size()));
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        return new Recommended_Items_DTO(recommendedItemsList);
    }

    public Recommended_Items_DTO getMainPageRecommendations() {

        List<Collections> collections = collectionsRepository.findAllByIsRecommendedInMainPage(true, Sort.by("sortOrder").ascending());
        List<Items> itemsList = new ArrayList<>();

        for (Collections collection : collections) {

            List<Items> randomItems = itemsRepository.findTop8RandomItemsByCollection(collection.getId());
            itemsList.addAll(randomItems);
        }

        return new Recommended_Items_DTO(itemsList);
    }


    @Transactional
    public List<Recommended_Items_DTO> update(RecommendedItemsRequest recommendedItemsRequest) {

        Items item = itemsRepository.findById(recommendedItemsRequest.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item " + recommendedItemsRequest.getItemId() + " Not Found"));

        recommendedItemsRepository.deleteAllByItem(item);

        return setRecommendedItems(recommendedItemsRequest, item);
    }

    private List<Recommended_Items_DTO> setRecommendedItems(RecommendedItemsRequest recommendedItemsRequest, Items item) {

        List<Recommended_Items> recommendedItemsList = new ArrayList<>();

        if (recommendedItemsRequest.getRecommendedItemsIds() == null || recommendedItemsRequest.getRecommendedItemsIds().isEmpty()) {

            return new ArrayList<>();
        }

        for (Long recommendedItemId : recommendedItemsRequest.getRecommendedItemsIds()) {

            if (!recommendedItemId.equals(item.getId())) {

                Items recommendedItemFound = itemsRepository.findById(recommendedItemId)
                        .orElseThrow(() -> new EntityNotFoundException("Recommended Item " + recommendedItemsRequest.getItemId() + " Not Found"));

                Recommended_Items recommendedItem = new Recommended_Items();

                recommendedItem.setItem(item);
                recommendedItem.setRecommendedItem(recommendedItemFound);

                recommendedItemsList.add(recommendedItem);
            }
        }

        recommendedItemsRepository.saveAll(recommendedItemsList);

        return recommendedItemsList.stream()
                .map(recommendedItem -> new Recommended_Items_DTO(
                        recommendedItemsList.stream()
                                .map(Recommended_Items::getRecommendedItem)
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }
}
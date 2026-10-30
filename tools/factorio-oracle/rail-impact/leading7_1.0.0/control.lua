local function emit(t) localised_print('LEADING '..game.table_to_json(t)) end
local function ent(e)
 if not e or not e.valid then return nil end
 local train=(e.type=='locomotive' or e.type=='cargo-wagon' or e.type=='fluid-wagon') and e.train or nil
 return {name=e.name,type=e.type,unit=e.unit_number,health=e.health,position=e.position,orientation=e.orientation,speed=train and train.speed,weight=train and train.weight,energy_per_hit_point=train and e.prototype.energy_per_hit_point}
end
local function train_state(l)
 if not l or not l.valid then return nil end
 local t=l.train; local cars={}; for i,e in ipairs(t.carriages) do cars[i]=ent(e) end
 return {speed=t.speed,weight=t.weight,state=t.state,path=t.has_path,front=ent(t.front_stock),back=ent(t.back_stock),cars=cars,fuel=l.burner.remaining_burning_fuel,heat=l.burner.heat,coal=l.get_fuel_inventory().get_item_count('coal')}
end
local function cargo(e)
 if not e or not e.valid then return nil end
 return e.get_inventory(defines.inventory.cargo_wagon).get_contents()
end
local function nearby()
 local result={}; local found=global.surface.find_entities_filtered{area={{48,-16},{80,16}},type={'item-entity','corpse'}}
 for _,e in ipairs(found) do
  local t=ent(e); if e.type=='item-entity' and e.stack.valid_for_read then t.stack={name=e.stack.name,count=e.stack.count,health=e.stack.health} end
  result[#result+1]=t
 end
 return result
end
script.on_init(function()
 local s=game.create_surface('leading7',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}});global.surface=s
 for y=-768,64,32 do s.request_to_generate_chunks({16,y},1) end; s.request_to_generate_chunks({64,0},1);s.force_generate_chunk_requests()
 for y=-720,40,2 do s.create_entity{name='straight-rail',position={16,y},direction=defines.direction.north,force='player'} end
 for y=-10,10,2 do s.create_entity{name='straight-rail',position={64,y},direction=defines.direction.north,force='player'} end
 local loco=s.create_entity{name='locomotive',position={16,0},direction=defines.direction.north,force='player'}
 local front=s.create_entity{name='fluid-wagon',position={16,-7},direction=defines.direction.north,force='player'}
 local biter=s.create_entity{name='small-biter',position={17,-200},force='enemy'}
 local stop=s.create_entity{name='train-stop',position={18,-700},direction=defines.direction.north,force='player'}
 local doomed=s.create_entity{name='cargo-wagon',position={64,0},direction=defines.direction.north,force='player'}
 if not loco or not front or not biter or not stop or not doomed then emit({kind='setup_error',loco=loco~=nil,front=front~=nil,biter=biter~=nil,stop=stop~=nil,doomed=doomed~=nil});return end
 biter.active=false; stop.backer_name='Leading7'; loco.get_fuel_inventory().insert{name='coal',count=30}
 doomed.get_inventory(defines.inventory.cargo_wagon).insert{name='iron-plate',count=17};doomed.health=20
 global.loco=loco;global.front=front;global.biter=biter;global.doomed=doomed
 emit({kind='setup',train=train_state(loco),biter=ent(biter),biter_mask=biter.prototype.collision_mask,biter_resistances=biter.prototype.resistances,front_mask=front.prototype.collision_mask,doomed=ent(doomed),contents=cargo(doomed),nearby=nearby()})
end)
script.on_event(defines.events.on_entity_damaged,function(e)
 emit({kind='damage',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type.name,original=e.original_damage_amount,final=e.final_damage_amount,final_health=e.final_health,train=train_state(global.loco)})
end)
script.on_event(defines.events.on_entity_died,function(e)
 emit({kind='died',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type and e.damage_type.name,contents=e.entity==global.doomed and cargo(e.entity) or nil,nearby=e.entity==global.doomed and nearby() or nil,train=train_state(global.loco)})
end)
script.on_event(defines.events.on_tick,function(e)
 local l=global.loco; if not l then return end
 if e.tick==0 then local t=l.train;t.schedule={current=1,records={{station='Leading7',wait_conditions={{type='time',compare_type='and',ticks=60}}}}};t.manual_mode=false end
 if e.tick==1 then
  local d=global.doomed;emit({kind='destruction_before',tick=e.tick,entity=ent(d),contents=cargo(d),nearby=nearby()})
  local result=d.damage(1000,game.forces.player,'impact')
  emit({kind='destruction_after',tick=e.tick,valid=d.valid,damage_return=result,nearby=nearby()})
 end
 if e.tick==2 or e.tick==5 or e.tick==30 then emit({kind='destruction_later',tick=e.tick,valid=global.doomed.valid,nearby=nearby()}) end
 emit({kind='trace',tick=e.tick,train=train_state(l),biter=ent(global.biter)})
end)
